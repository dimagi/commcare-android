package org.commcare.xml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.crypto.Cipher;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.CaseIndexTable;
import org.commcare.android.database.user.models.EntityStorageCache;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.references.JavaHttpReference;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.util.PropertyUtils;
import org.kxml2.io.KXmlParser;

import android.net.ParseException;
import android.net.Uri;
import android.util.Pair;

/**
 * @author ctsims
 *
 */
public class AndroidCaseXmlParser extends CaseXmlParser {
    Cipher attachmentCipher;
    Cipher userCipher;
    File folder;
    boolean processAttachments = true;
    HttpRequestGenerator generator;
    EntityStorageCache mEntityCache;
    CaseIndexTable mCaseIndexTable;
    
    public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage, EntityStorageCache entityCache, CaseIndexTable indexTable) {
        super(parser, storage);
        mEntityCache = entityCache;
        mCaseIndexTable = indexTable;
    }    
    
    public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage) {
        this(parser, storage, new EntityStorageCache("case"), new CaseIndexTable());
    }
    
    //TODO: Sync the following two constructors!
    public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage, Cipher attachmentCipher, Cipher userCipher, File folder) {
        this(parser, storage);
        this.attachmentCipher = attachmentCipher;
        this.userCipher = userCipher;
        this.folder = folder;
        processAttachments = true;
    }

    public AndroidCaseXmlParser(KXmlParser parser, int[] tallies, boolean b, SqlStorage<ACase> storage, HttpRequestGenerator generator) {
        super(parser, tallies, b, storage);
        this.generator = generator;
        mEntityCache = new EntityStorageCache("case");
        mCaseIndexTable = new CaseIndexTable();
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.xml.CaseXmlParser#removeAttachment(org.commcare.cases.model.Case, java.lang.String)
     */
    @Override
    protected void removeAttachment(Case caseForBlock, String attachmentName) {
        if(!processAttachments) { return;}
        
        //TODO: All of this code should really be somewhere else, too, since we also need to remove attachments on
        //purge.
        String source = caseForBlock.getAttachmentSource(attachmentName);
        
        //TODO: Handle remote reference download?
        if(source == null) { return;}

        //Handle these cases better later.
        try {
            ReferenceManager._().DeriveReference(source).remove();
        } catch (InvalidReferenceException | IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void commit(Case parsed) throws IOException {
        SQLiteDatabase db;
        try {
            db = CommCareApplication._().getUserDbHandle();
        } catch (SessionUnavailableException e) {
            throw new UserStorageClosedException("User database closed while parsing");
        }
        db.beginTransaction();
        try {
            super.commit(parsed);
            mEntityCache.invalidateCache(String.valueOf(parsed.getID()));
            mCaseIndexTable.clearCaseIndices(parsed);
            mCaseIndexTable.indexCase(parsed);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.xml.CaseXmlParser#processAttachment(java.lang.String, java.lang.String, java.lang.String, org.kxml2.io.KXmlParser)
     */
    @Override
    protected String processAttachment(String src, String from, String name, KXmlParser parser) {
        if(!processAttachments) { return null;}
        
        //We need to figure out whether or not the attachment is local to the device or in a remote location.
        if(CaseXmlParser.ATTACHMENT_FROM_LOCAL.equals(from)) {
            //Parse from the local environment
            if(folder == null) { return null; }
            File source = new File(folder, src);

            Pair<File, String> dest = getDestination(source.getName());
            
            try {
                FileUtil.copyFile(source, dest.first, null, null);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            
            return dest.second;
        } else if(CaseXmlParser.ATTACHMENT_FROM_REMOTE.equals(from)) {
            //The attachment is in remote location.
            try {
                Reference remote = ReferenceManager._().DeriveReference(src);
                
                //TODO: Awful.
                if(remote instanceof JavaHttpReference) {
                    ((JavaHttpReference)remote).setHttpRequestor(generator);
                }
                
                
                //TODO: Proper URL here    
                Pair<File, String> dest = getDestination(src);
                
                boolean readAttachment = false;
                
                int tries = 2;
                for(int i = 1 ; i <= tries ; i++) {
                    
                    //Delete any existing file where the incoming file is going
                    //(only relevant if we're retrying)
                    if(dest.first.exists()) { dest.first.delete(); }
                    
                    try {
                        dest.first.createNewFile();
                    } catch(IOException fe) {
                        Logger.log(AndroidLogger.TYPE_RESOURCES, "Couldn't create placeholder for new file at " + dest.first.getAbsolutePath());
                    }
                    try {
                        AndroidStreamUtil.writeFromInputToOutput(remote.getStream(), new FileOutputStream(dest.first));
                        readAttachment = true;
                        break;
                    } catch (IOException e) {
                        Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Failed reading (attempt #" + tries + ") attachment from " + src);
                    }
                }
                
                if(!readAttachment ) {
                    Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Failed to read attachment from " + src);
                    return null;
                }
                
                return dest.second;
                //TODO:  Don't Pass code review without fixing this exception handling
            } catch(ParseException e) {
                
            } catch (InvalidReferenceException e) {
                //We can't go fetch this resource because we don't have access to the reference type
                Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Couldn't find attachment at reference " + e.getReferenceString());
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
     * @return
     */
    private Pair<File, String> getDestination(String source) {
        File storagePath = new File(CommCareApplication._().getCurrentApp().fsPath(GlobalConstants.FILE_CC_ATTACHMENTS));
        String dest = PropertyUtils.genUUID().replace("-", "");
        
        //add an extension
        String fileName = Uri.parse(source).getLastPathSegment();
        if(fileName != null) {
            int lastDot = fileName.lastIndexOf(".");
            if(lastDot != -1) {
                dest += fileName.substring(lastDot);
            }
        }
        
        return new Pair<File, String>(new File(storagePath, dest), GlobalConstants.ATTACHMENT_REF + dest);
    }


    /* (non-Javadoc)
     * @see org.commcare.xml.CaseXmlParser#CreateCase(java.lang.String, java.lang.String)
     */
    @Override
    protected Case CreateCase(String name, String typeId) {
        return new ACase(name, typeId);
    }
}
