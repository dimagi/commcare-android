package org.commcare.tasks;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.util.Pair;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.engine.extensions.CalendaredDateFormatHandler;
import org.commcare.engine.extensions.IntentExtensionParser;
import org.commcare.engine.extensions.PollSensorAction;
import org.commcare.engine.extensions.PollSensorExtensionParser;
import org.commcare.engine.extensions.XFormExtensionUtils;
import org.commcare.logging.AndroidLogger;
import org.commcare.logic.FileReferenceFactory;
import org.commcare.logic.FormController;
import org.commcare.models.database.DbUtil;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.FileUtil;
import org.commcare.utils.GlobalConstants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.spec.SecretKeySpec;

/**
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public abstract class FormLoaderTask<R> extends CommCareTask<Uri, String, FormLoaderTask.FECWrapper, R> {
    public static InstanceInitializationFactory iif;

    private final SecretKeySpec mSymetricKey;
    private final boolean mReadOnly;
    private final boolean recordEntrySession;

    private final R activity;

    private FECWrapper data;

    public static final int FORM_LOADER_TASK_ID = 16;

    public FormLoaderTask(SecretKeySpec symetricKey, boolean readOnly,
                          boolean recordEntrySession, R activity) {
        this.mSymetricKey = symetricKey;
        this.mReadOnly = readOnly;
        this.activity = activity;
        this.taskId = FORM_LOADER_TASK_ID;
        this.recordEntrySession = recordEntrySession;
        TAG = FormLoaderTask.class.getSimpleName();
    }

    /**
     * Initialize {@link FormEntryController} with {@link FormDef} from binary or from XML. If given
     * an instance, it will be used to fill the {@link FormDef}.
     */
    @Override
    protected FECWrapper doTaskBackground(Uri... form) {
        FormDef fd = null;

        Pair<String, String> formAndMediaPaths = getFormAndMediaPaths(form[0]);

        String formPath = formAndMediaPaths.first;
        String formMediaPath = formAndMediaPaths.second;

        File formXml = new File(formPath);
        String formHash = FileUtil.getMd5Hash(formXml);
        File formBin = getCachedForm(formHash);

        if (formBin.exists()) {
            // if we have binary, deserialize binary
            Log.i(TAG, "Attempting to load " + formXml.getName() +
                    " from cached file: " + formBin.getAbsolutePath());
            fd = deserializeFormDef((Context)activity, formBin);
            if (fd == null) {
                Logger.log(AndroidLogger.SOFT_ASSERT,
                        "Deserialization of " + formXml.getName() + " form failed.");
                // Remove the file, and make a new .formdef from xml
                formBin.delete();
            }
        }

        // If we couldn't find a cached version, load the form from the XML
        if (fd == null) {
            fd = loadFormFromFile(formXml);
        }

        // Try to write the form definition to a cached location
        try {
            serializeFormDef(fd, formPath);
        } catch (Exception e) {
            // The cache is a bonus, so if we can't write it, don't crash, but log 
            // it so we can clean up whatever is preventing the cached version from
            // working
            Logger.log(AndroidLogger.TYPE_RESOURCES, "XForm could not be serialized. Error trace:\n" + ExceptionReporting.getStackTrace(e));
        }

        FormEntryController fec = initFormDef(fd);

        // Remove previous forms
        ReferenceManager._().clearSession();

        setupFormMedia(formMediaPath, formXml);

        FormController fc = new FormController(fec, mReadOnly);

        data = new FECWrapper(fc);
        return data;
    }

    private Pair<String, String> getFormAndMediaPaths(Uri formUri) {
        Cursor c = null;
        try {
            //TODO: Selection=? helper
            c = ((Context)activity).getContentResolver().query(formUri, new String[]{FormsProviderAPI.FormsColumns.FORM_FILE_PATH, FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH}, null, null, null);

            if (c == null || !c.moveToFirst()) {
                throw new IllegalArgumentException("Invalid Form URI Provided! No form content found at URI: " + formUri.toString());
            }

            return new Pair<>(c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH)),
                    c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH)));
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private FormDef loadFormFromFile(File formXmlFile) {
        FileInputStream fis;
        // no binary, read from xml
        Log.i(TAG, "Attempting to load from: " + formXmlFile.getAbsolutePath());
        try {
            fis = new FileInputStream(formXmlFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error reading XForm file");
        }
        XFormParser.registerHandler("intent", new IntentExtensionParser());
        XFormParser.registerActionHandler(PollSensorAction.ELEMENT_NAME, new PollSensorExtensionParser());
        FormDef fd = XFormExtensionUtils.getFormFromInputStream(fis);
        if (fd == null) {
            throw new RuntimeException("Error reading XForm file");
        }
        return fd;
    }

    private FormEntryController initFormDef(FormDef formDef) {
        formDef.exprEvalContext.addFunctionHandler(new CalendaredDateFormatHandler((Context)activity));
        // create FormEntryController from formdef
        FormEntryModel fem = new FormEntryModel(formDef);
        FormEntryController fec;
        if (recordEntrySession) {
            fec = FormEntryController.buildRecordingController(fem);
        } else {
            fec = new FormEntryController(fem);
        }

        //TODO: Get a reasonable IIF object
        // import existing data into formdef
        if (FormEntryActivity.mInstancePath != null) {
            // This order is important. Import data, then initialize.
            importData(FormEntryActivity.mInstancePath, fec);
            formDef.initialize(false, iif);
        } else {
            formDef.initialize(true, iif);
        }
        if (mReadOnly) {
            formDef.getInstance().getRoot().setEnabled(false);
        }
        return fec;
    }

    private void setupFormMedia(String formMediaPath, File formXmlFile) {
        if (formMediaPath != null) {
            ReferenceManager._().addSessionRootTranslator(
                    new RootTranslator("jr://images/", formMediaPath));
            ReferenceManager._().addSessionRootTranslator(
                    new RootTranslator("jr://audio/", formMediaPath));
            ReferenceManager._().addSessionRootTranslator(
                    new RootTranslator("jr://video/", formMediaPath));
        } else {
            // This should get moved to the Application Class
            if (ReferenceManager._().getFactories().length == 0) {
                // this is /sdcard/odk
                ReferenceManager._().addReferenceFactory(
                        new FileReferenceFactory(Environment.getExternalStorageDirectory() + "/odk"));
            }

            // set paths to /sdcard/odk/forms/formfilename-media/
            String formFileName = formXmlFile.getName().substring(0, formXmlFile.getName().lastIndexOf("."));

            // Set jr://... to point to /sdcard/odk/forms/filename-media/
            ReferenceManager._().addSessionRootTranslator(
                    new RootTranslator("jr://images/", "jr://file/forms/" + formFileName + "-media/"));
            ReferenceManager._().addSessionRootTranslator(
                    new RootTranslator("jr://audio/", "jr://file/forms/" + formFileName + "-media/"));
            ReferenceManager._().addSessionRootTranslator(
                    new RootTranslator("jr://video/", "jr://file/forms/" + formFileName + "-media/"));
        }
    }

    private boolean importData(String filePath, FormEntryController fec) {
        // convert files into a byte array
        InputStream is;
        try {
            is = EncryptionIO.getFileInputStream(filePath, mSymetricKey);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to open encrypted form instance file: " + filePath);
        }

        // get the root of the saved and template instances
        TreeElement savedRoot;
        try {
            savedRoot = XFormParser.restoreDataModel(is, null).getRoot();
        } catch (IOException e) {
            e.printStackTrace();
            throw new XFormParseException("Bad parsing from byte array " + e.getMessage());
        }
        TreeElement templateRoot = fec.getModel().getForm().getInstance().getRoot().deepCopy(true);

        // weak check for matching forms
        if (!savedRoot.getName().equals(templateRoot.getName()) || savedRoot.getMult() != 0) {
            Log.e(TAG, "Saved form instance does not match template form definition");
            return false;
        } else {
            // populate the data model
            TreeReference tr = TreeReference.rootRef();
            tr.add(templateRoot.getName(), TreeReference.INDEX_UNBOUND);
            templateRoot.populate(savedRoot);

            // populated model to current form
            fec.getModel().getForm().getInstance().setRoot(templateRoot);

            // fix any language issues
            // : http://bitbucket.org/javarosa/main/issue/5/itext-n-appearing-in-restored-instances
            if (fec.getModel().getLanguages() != null) {
                fec.getModel()
                        .getForm()
                        .localeChanged(fec.getModel().getLanguage(),
                                fec.getModel().getForm().getLocalizer());
            }
            return true;
        }
    }

    /**
     * Read serialized {@link FormDef} from file and recreate as object.
     */
    private static FormDef deserializeFormDef(Context context, File formDefFile) {
        FileInputStream fis;
        FormDef fd;
        try {
            // create new form def
            fd = new FormDef();
            fis = new FileInputStream(formDefFile);
            DataInputStream dis = new DataInputStream(new BufferedInputStream(fis));

            // read serialized formdef into new formdef
            fd.readExternal(dis, DbUtil.getPrototypeFactory(context));
            dis.close();
        } catch (Throwable e) {
            e.printStackTrace();
            fd = null;
        }

        return fd;
    }

    /**
     * Write the FormDef to the file system as a binary blob.
     */
    private void serializeFormDef(FormDef fd, String formFilePath) throws IOException {
        // calculate unique md5 identifier for this form
        String hash = FileUtil.getMd5Hash(new File(formFilePath));
        File formDef = getCachedForm(hash);

        // create a serialized form file if there isn't already one at this hash
        if (!formDef.exists()) {
            OutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(formDef);
                DataOutputStream dos;
                outputStream = dos = new DataOutputStream(outputStream);
                fd.writeExternal(dos);
                dos.flush();
            } finally {
                //make sure we clean up the stream
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        // Swallow this. If we threw an exception from inside the 
                        // try, this close exception will trump it on the return 
                        // path, and we care a lot more about that exception
                        // than this one.
                    }
                }
            }
        }
    }

    private File getCachedForm(String hash) {
        return new File(CommCareApplication._().getCurrentApp().
                fsPath(GlobalConstants.FILE_CC_CACHE) + "/" + hash + ".formdef");
    }

    public void destroy() {
        if (data != null) {
            data.free();
            data = null;
        }
    }

    protected static class FECWrapper {
        FormController controller;

        protected FECWrapper(FormController controller) {
            this.controller = controller;
        }

        public FormController getController() {
            return controller;
        }

        protected void free() {
            controller = null;
        }
    }
}
