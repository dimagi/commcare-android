package org.commcare.android.resource.installers;

import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.javarosa.PollSensorAction;
import org.commcare.engine.extensions.IntentExtensionParser;
import org.commcare.engine.extensions.PollSensorExtensionParser;
import org.commcare.engine.extensions.XFormExtensionUtils;
import org.commcare.resources.model.MissingMediaException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.GlobalConstants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.Reference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localizer;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * @author ctsims
 */
public class XFormAndroidInstaller extends FileSystemInstaller {
    private static final String TAG = XFormAndroidInstaller.class.getSimpleName();

    private String namespace;
    protected int formDefId = -1;
    private boolean isUpdateInfoForm = false;

    @SuppressWarnings("unused")
    public XFormAndroidInstaller() {
        // for externalization
    }

    public XFormAndroidInstaller(String localDestination, String upgradeDestination, boolean isUpdateInfoForm) {
        super(localDestination, upgradeDestination);
        this.isUpdateInfoForm = isUpdateInfoForm;
    }

    public XFormAndroidInstaller(String localLocation, String localDestination, String upgradeDestination, String namespace, int formDefId) {
        super(localLocation, localDestination, upgradeDestination);
        this.namespace = namespace;
        this.formDefId = formDefId;
    }

    @Override
    public boolean initialize(AndroidCommCarePlatform platform, boolean isUpgrade) {
        if (isUpdateInfoForm) {
            platform.setUpdateInfoXFormId(formDefId);
        }

        platform.registerXmlns(namespace, formDefId);
        return true;
    }

    @Override
    protected int customInstall(Resource r, Reference local, boolean upgrade, AndroidCommCarePlatform platform) throws IOException, UnresolvedResourceException {
        registerAndroidLevelFormParsers();
        FormDef formDef;
        try {
            formDef = XFormExtensionUtils.getFormFromInputStream(local.getStream());
        } catch (XFormParseException xfpe) {
            throw new UnresolvedResourceException(r, xfpe.getMessage(), true);
        }

        this.namespace = formDef.getInstance().schema;
        if (namespace == null) {
            throw new UnresolvedResourceException(r, "Invalid XForm, no namespace defined", true);
        }

        Vector<Integer> existingforms = FormDefRecord.getFormDefIdsByJrFormId(platform.getFormDefStorage(), formDef.getMainInstance().schema);
        if (existingforms != null && existingforms.size() > 0) {
            //we already have one form. Hopefully this is during an upgrade...
            if (!upgrade) {
                //Hm, error out?
                Logger.log(LogTypes.SOFT_ASSERT, "Form with schema " + formDef.getMainInstance().schema + " already present during the install");
            }

            //So we know there's another form here. We should wait until it's time for
            //the upgrade and replace the pointer to here.
            formDefId = existingforms.get(0);

            if (existingforms.size() > 1) {
                Logger.log(LogTypes.SOFT_ASSERT, "More than one Form with schema " + formDef.getMainInstance().schema + "present during the install");
            }
        } else {
            FormDefRecord formDefRecord = new FormDefRecord("NAME", formDef.getMainInstance().schema, local.getLocalURI(), GlobalConstants.MEDIA_REF);
            formDefId = formDefRecord.save(platform.getFormDefStorage());
        }

        return upgrade ? Resource.RESOURCE_STATUS_UPGRADE : Resource.RESOURCE_STATUS_INSTALLED;
    }

    public static void registerAndroidLevelFormParsers() {
        //Ugh. Really need to sync up the Xform libs between ccodk and odk.
        XFormParser.registerHandler("intent", new IntentExtensionParser());
        XFormParser.registerActionHandler(PollSensorAction.ELEMENT_NAME, new PollSensorExtensionParser());
    }

    @Override
    public boolean upgrade(Resource r, AndroidCommCarePlatform platform) {
        boolean fileUpgrade = super.upgrade(r, platform);
        return fileUpgrade && updateFilePath(platform);
    }

    /**
     * At some point hopefully soon we're not going to be shuffling our xforms around like crazy, so updates will mostly involve
     * just changing where the provider points.
     */
    private boolean updateFilePath(CommCarePlatform platform) {
        String localRawUri;
        try {
            localRawUri = ReferenceManager.instance().DeriveReference(this.localLocation).getLocalURI();
        } catch (InvalidReferenceException e) {
            Logger.log(LogTypes.TYPE_RESOURCES, "Installed resource wasn't able to be derived from " + localLocation);
            return false;
        }

        //Update the form file path
        FormDefRecord.updateFilePath(((AndroidCommCarePlatform)platform).getFormDefStorage(), formDefId, new File(localRawUri).getAbsolutePath());
        return true;
    }

    @Override
    public boolean revert(Resource r, ResourceTable table, CommCarePlatform platform) {
        return super.revert(r, table, platform) && updateFilePath(platform);
    }

    @Override
    public int rollback(Resource r, CommCarePlatform platform) {
        int newStatus = super.rollback(r, platform);
        if (newStatus == Resource.RESOURCE_STATUS_INSTALLED) {
            if (updateFilePath(platform)) {
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
        this.formDefId = ExtUtil.readInt(in);
        this.isUpdateInfoForm = ExtUtil.readBool(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);
        ExtUtil.writeString(out, ExtUtil.emptyIfNull(namespace));
        ExtUtil.writeNumeric(out, formDefId);
        ExtUtil.writeBool(out, isUpdateInfoForm);
    }

    @Override
    public boolean verifyInstallation(Resource r,
                                      Vector<MissingMediaException> problems,
                                      CommCarePlatform platform) {
        //Check to see whether the formDef exists and reads correctly
        FormDef formDef;
        try {
            Reference local = ReferenceManager.instance().DeriveReference(localLocation);
            formDef = new XFormParser(new InputStreamReader(local.getStream(), "UTF-8")).parse();
        } catch (Exception e) {
            // something weird/bad happened here. first make sure storage is available
            if (!CommCareApplication.instance().isStorageAvailable()) {
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
        if (localizer == null) {
            //things are fine
            return false;
        }
        for (String locale : localizer.getAvailableLocales()) {
            Hashtable<String, String> localeData = localizer.getLocaleData(locale);
            for (Enumeration en = localeData.keys(); en.hasMoreElements(); ) {
                String key = (String)en.nextElement();
                if (key.contains(";")) {
                    //got some forms here
                    String form = key.substring(key.indexOf(";") + 1, key.length());
                    if (form.equals(FormEntryCaption.TEXT_FORM_VIDEO) ||
                            form.equals(FormEntryCaption.TEXT_FORM_AUDIO) ||
                            form.equals(FormEntryCaption.TEXT_FORM_IMAGE)) {
                        try {

                            String externalMedia = localeData.get(key);
                            Reference ref = ReferenceManager.instance().DeriveReference(externalMedia);
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
