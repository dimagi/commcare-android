package org.commcare.xml;

import android.net.ParseException;
import android.net.Uri;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.model.Case;
import org.commcare.engine.references.JavaHttpReference;
import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.models.database.user.models.CommCareEntityStorageCache;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.PropertyUtils;
import org.kxml2.io.KXmlParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.annotation.Nullable;

/**
 * @author ctsims
 */
public class AndroidCaseXmlParser extends CaseXmlParser {
    private File folder;
    private final boolean processAttachments = true;
    private CommcareRequestEndpoints generator;
    @Nullable
    private final CommCareEntityStorageCache mEntityCache;
    private final AndroidCaseIndexTable mCaseIndexTable;

    public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage,
                                @Nullable CommCareEntityStorageCache entityCache, AndroidCaseIndexTable indexTable) {
        super(parser, storage);
        mEntityCache = entityCache;
        mCaseIndexTable = indexTable;
    }

    public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage) {
        this(parser, storage, new CommCareEntityStorageCache("case"), new AndroidCaseIndexTable());
    }

    public AndroidCaseXmlParser(KXmlParser parser, boolean acceptCreateOverwrites,
                                IStorageUtilityIndexed<Case> storage,
                                CommcareRequestEndpoints generator) {
        super(parser, acceptCreateOverwrites, storage);
        this.generator = generator;
        mEntityCache = new CommCareEntityStorageCache("case");
        mCaseIndexTable = new AndroidCaseIndexTable();
    }

    @Override
    protected void removeAttachment(Case caseForBlock, String attachmentName) {
        if (!processAttachments) {
            return;
        }

        //TODO: All of this code should really be somewhere else, too, since we also need to remove attachments on
        //purge.
        String source = caseForBlock.getAttachmentSource(attachmentName);

        //TODO: Handle remote reference download?
        if (source == null) {
            return;
        }

        //Handle these cases better later.
        try {
            ReferenceManager.instance().DeriveReference(source).remove();
        } catch (InvalidReferenceException | IOException e) {
            e.printStackTrace();
        }
    }

    protected SQLiteDatabase getDbHandle() {
        return CommCareApplication.instance().getUserDbHandle();
    }

    @Override
    public void commit(Case parsed) throws IOException {
        SQLiteDatabase db;
        db = getDbHandle();
        db.beginTransaction();
        try {
            super.commit(parsed);
            if (mEntityCache != null) {
                mEntityCache.invalidateRecord(String.valueOf(parsed.getID()));
            }
            mCaseIndexTable.clearCaseIndices(parsed);
            mCaseIndexTable.indexCase(parsed);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    protected String processAttachment(String src, String from, String name, KXmlParser parser) {
        if (!processAttachments) {
            return null;
        }

        //We need to figure out whether or not the attachment is local to the device or in a remote location.
        if (CaseXmlParserUtil.ATTACHMENT_FROM_LOCAL.equals(from)) {
            //Parse from the local environment
            if (folder == null) {
                return null;
            }
            File source = new File(folder, src);

            Pair<File, String> dest = getDestination(source.getName());

            try {
                FileUtil.copyFile(source, dest.first, null, null);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }

            return dest.second;
        } else if (CaseXmlParserUtil.ATTACHMENT_FROM_REMOTE.equals(from)) {
            //The attachment is in remote location.
            try {
                Reference remote = ReferenceManager.instance().DeriveReference(src);

                //TODO: Awful.
                if (remote instanceof JavaHttpReference) {
                    ((JavaHttpReference)remote).setHttpRequestor(generator);
                }


                //TODO: Proper URL here    
                Pair<File, String> dest = getDestination(src);

                boolean readAttachment = false;

                int tries = 2;
                for (int i = 1; i <= tries; i++) {

                    //Delete any existing file where the incoming file is going
                    //(only relevant if we're retrying)
                    if (dest.first.exists()) {
                        dest.first.delete();
                    }

                    try {
                        dest.first.createNewFile();
                    } catch (IOException fe) {
                        Logger.log(LogTypes.TYPE_RESOURCES, "Couldn't create placeholder for new file at " + dest.first.getAbsolutePath());
                    }
                    try {
                        StreamsUtil.writeFromInputToOutputNew(remote.getStream(), new FileOutputStream(dest.first));
                        readAttachment = true;
                        break;
                    } catch (IOException e) {
                        Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Failed reading (attempt #" + tries + ") attachment from " + src);
                    }
                }

                if (!readAttachment) {
                    Logger.log(LogTypes.TYPE_WARNING_NETWORK, "Failed to read attachment from " + src);
                    return null;
                }

                return dest.second;
                //TODO:  Don't Pass code review without fixing this exception handling
            } catch (ParseException e) {

            } catch (InvalidReferenceException e) {
                //We can't go fetch this resource because we don't have access to the reference type
                Logger.log(LogTypes.TYPE_ERROR_DESIGN, "Couldn't find attachment at reference " + e.getReferenceString());
                return null;
            }
            return null;
        }
        return null;
    }

    /**
     * Find the location for a local attachment. This location will be in the attachment
     * folder, and will share the extension of the source file if available. The filename
     * will be randomized, however.
     *
     * @param source the full path of the source of the attachment.
     */
    private Pair<File, String> getDestination(String source) {
        File storagePath = new File(CommCareApplication.instance().getCurrentApp().fsPath(GlobalConstants.FILE_CC_ATTACHMENTS));
        String dest = PropertyUtils.genUUID().replace("-", "");

        //add an extension
        String fileName = Uri.parse(source).getLastPathSegment();
        if (fileName != null) {
            int lastDot = fileName.lastIndexOf(".");
            if (lastDot != -1) {
                dest += fileName.substring(lastDot);
            }
        }

        return new Pair<>(new File(storagePath, dest), GlobalConstants.ATTACHMENT_REF + dest);
    }

    @Override
    protected Case buildCase(String name, String typeId) {
        return new ACase(name, typeId);
    }
}
