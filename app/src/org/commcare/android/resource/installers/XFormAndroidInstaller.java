package org.commcare.android.resource.installers;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.FormsProviderAPI;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceInitializationException;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.core.util.OrderedHashtable;
import org.javarosa.core.util.PrefixTreeNode;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;
import org.odk.collect.android.jr.extensions.IntentExtensionParser;
import org.odk.collect.android.jr.extensions.PollSensorExtensionParser;
import org.odk.collect.android.jr.extensions.XFormExtensionUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Vector;

/**
 * @author ctsims
 */
public class XFormAndroidInstaller extends FileSystemInstaller {
    private static final String TAG = XFormAndroidInstaller.class.getSimpleName();

    private String namespace;

    private String contentUri;

    public XFormAndroidInstaller() {
        // for externalization
    }

    public XFormAndroidInstaller(String localDestination, String upgradeDestination) {
        super(localDestination, upgradeDestination);
    }

    @Override
    public boolean initialize(AndroidCommCarePlatform instance) throws ResourceInitializationException {
        instance.registerXmlns(namespace, contentUri);
        return true;
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade) throws IOException, UnresolvedResourceException {
        //Ugh. Really need to sync up the Xform libs between ccodk and odk.
        XFormParser.registerHandler("intent", new IntentExtensionParser());
        XFormParser.registerStructuredAction("pollsensor", new PollSensorExtensionParser());

        FormDef formDef;
        try {
            formDef = XFormExtensionUtils.getFormFromInputStream(local.getStream());
        } catch(XFormParseException xfpe) {
            throw new UnresolvedResourceException(r, xfpe.getMessage(), true);
        }


        this.namespace = formDef.getInstance().schema;
        if (namespace == null) {
            throw new UnresolvedResourceException(r, "Invalid XForm, no namespace defined", true);
        }


        //TODO: Where should this context be?
        ContentResolver cr = CommCareApplication._().getContentResolver();
        ContentProviderClient cpc = cr.acquireContentProviderClient(FormsProviderAPI.FormsColumns.CONTENT_URI);

        ContentValues cv = new ContentValues();
        cv.put(FormsProviderAPI.FormsColumns.DISPLAY_NAME, "NAME");
        cv.put(FormsProviderAPI.FormsColumns.DESCRIPTION, "NAME"); //nullable
        cv.put(FormsProviderAPI.FormsColumns.JR_FORM_ID, formDef.getMainInstance().schema); // ? 
        cv.put(FormsProviderAPI.FormsColumns.FORM_FILE_PATH, local.getLocalURI());
        cv.put(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH, GlobalConstants.MEDIA_REF);
        //cv.put(FormsProviderAPI.FormsColumns.SUBMISSION_URI, "NAME"); //nullable
        //cv.put(FormsProviderAPI.FormsColumns.BASE64_RSA_PUBLIC_KEY, "NAME"); //nullable


        Cursor existingforms = null;
        try {
            existingforms = cr.query(FormsProviderAPI.FormsColumns.CONTENT_URI,
                    new String[]{FormsProviderAPI.FormsColumns._ID},
                    FormsProviderAPI.FormsColumns.JR_FORM_ID + "=?",
                    new String[]{formDef.getMainInstance().schema}, null);


            if (existingforms != null && existingforms.moveToFirst()) {
                //we already have one form. Hopefully this is during an upgrade...
                if (!upgrade) {
                    //Hm, error out?
                }

                //So we know there's another form here. We should wait until it's time for
                //the upgrade and replace the pointer to here.
                Uri recordId = ContentUris.withAppendedId(FormsProviderAPI.FormsColumns.CONTENT_URI, existingforms.getLong(0));

                //Grab the URI we should update
                this.contentUri = recordId.toString();

                //TODO: Check to see if there is more than one form, and deal

            } else {
                Uri result = cpc.insert(FormsProviderAPI.FormsColumns.CONTENT_URI, cv);
                this.contentUri = result.toString();
            }
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new IOException("couldn't talk to form database to install form");
        } finally {
            if (existingforms != null) {
                existingforms.close();
            }
        }

        if (cpc != null) {
            cpc.release();
        }

        return upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED;
    }

    @Override
    public boolean upgrade(Resource r) {
        boolean fileUpgrade = super.upgrade(r);
        return fileUpgrade && updateFilePath();

    }

    /**
     * At some point hopefully soon we're not going to be shuffling our xforms around like crazy, so updates will mostly involve
     * just changing where the provider points.
     */
    private boolean updateFilePath() {
        String localRawUri;
        try {
            localRawUri = ReferenceManager._().DeriveReference(this.localLocation).getLocalURI();
        } catch (InvalidReferenceException e) {
            Logger.log(AndroidLogger.TYPE_RESOURCES, "Installed resource wasn't able to be derived from " + localLocation);
            return false;
        }

        //We're maintaining this whole Content setup now, so we've goota update things when we move them.
        ContentResolver cr = CommCareApplication._().getContentResolver();

        ContentValues cv = new ContentValues();
        cv.put(FormsProviderAPI.FormsColumns.FORM_FILE_PATH, new File(localRawUri).getAbsolutePath());

        //Update the form file path
        int updatedRows = cr.update(Uri.parse(this.contentUri), cv, null, null);
        if (updatedRows > 1) {
            throw new RuntimeException("Bad URI stored for xforms installer: " + this.contentUri);
        }
        return updatedRows != 0;
    }

    public boolean revert(Resource r, ResourceTable table) {
        return super.revert(r, table) && updateFilePath();
    }

    public int rollback(Resource r) {
        int newStatus = super.rollback(r);
        if (newStatus == Resource.RESOURCE_STATUS_INSTALLED) {
            if (updateFilePath()) {
                return newStatus;
            } else {
                //BOOO!
                return -1;
            }
        } else {
            return newStatus;
        }
    }


    @Override
    public boolean requiresRuntimeInitialization() {
        return true;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        super.readExternal(in, pf);
        this.namespace = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
        this.contentUri = ExtUtil.nullIfEmpty(ExtUtil.readString(in));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(namespace));
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(contentUri));
    }

    public boolean verifyInstallation(Resource r, Vector<MissingMediaException> problems) {
        //Check to see whether the formDef exists and reads correctly
        FormDef formDef;
        try {
            Reference local = ReferenceManager._().DeriveReference(localLocation);
            formDef = new XFormParser(new InputStreamReader(local.getStream(), "UTF-8")).parse();
        } catch (Exception e) {
            // something weird/bad happened here. first make sure storage is available
            if (!CommCareApplication._().isStorageAvailable()) {
                problems.addElement(new MissingMediaException(r, "Couldn't access your persisent storage. Please make sure your SD card is connected properly"));
            }

            problems.addElement(new MissingMediaException(r, "Form did not properly save into persistent storage"));
            return true;
        }
        if (formDef == null) {
            Log.d(TAG, "formdef is null");
        }
        //Otherwise, we want to figure out if the form has media, and we need to see whether it's properly
        //available
        Localizer localizer = formDef.getLocalizer();
        //get this out of the memory ASAP!
        formDef = null;
        if (localizer == null) {
            //things are fine
            return false;
        }
        for (String locale : localizer.getAvailableLocales()) {
            OrderedHashtable<String, PrefixTreeNode> localeData = localizer.getLocaleData(locale);
            for (Enumeration en = localeData.keys(); en.hasMoreElements(); ) {
                String key = (String) en.nextElement();
                if (key.contains(";")) {
                    //got some forms here
                    String form = key.substring(key.indexOf(";") + 1, key.length());
                    if (form.equals(FormEntryCaption.TEXT_FORM_VIDEO) ||
                            form.equals(FormEntryCaption.TEXT_FORM_AUDIO) ||
                            form.equals(FormEntryCaption.TEXT_FORM_IMAGE)) {
                        try {

                            String externalMedia = localeData.get(key).render();
                            Reference ref = ReferenceManager._().DeriveReference(externalMedia);
                            String localName = ref.getLocalURI();
                            try {
                                if (!ref.doesBinaryExist()) {
                                    problems.addElement(new MissingMediaException(r, "Missing external media: " + localName, externalMedia));
                                }
                            } catch (IOException e) {
                                problems.addElement(new MissingMediaException(r, "Problem reading external media: " + localName, externalMedia));
                            }
                        } catch (InvalidReferenceException e) {
                            //So the problem is that this might be a valid entry that depends on context
                            //in the form, so we'll ignore this situation for now.
                        }
                    }
                }
            }
        }
        return problems.size() != 0;
    }
}
