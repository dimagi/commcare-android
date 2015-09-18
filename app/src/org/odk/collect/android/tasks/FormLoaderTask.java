package org.odk.collect.android.tasks;

import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.FormsProviderAPI;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;
import org.javarosa.xform.parse.XFormParser;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.jr.extensions.CalendaredDateFormatHandler;
import org.odk.collect.android.jr.extensions.IntentExtensionParser;
import org.odk.collect.android.jr.extensions.PollSensorExtensionParser;
import org.odk.collect.android.jr.extensions.XFormExtensionUtils;
import org.odk.collect.android.listeners.FormLoaderListener;
import org.odk.collect.android.logic.FileReferenceFactory;
import org.odk.collect.android.logic.FormController;
import org.odk.collect.android.utilities.ApkUtils;
import org.odk.collect.android.utilities.FileUtils;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.spec.SecretKeySpec;

/**
 * Background task for loading a form.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public abstract class FormLoaderTask<R extends FragmentActivity> extends CommCareTask<Uri, String, FormLoaderTask.FECWrapper, R> {
    public static InstanceInitializationFactory iif;
    private final static String TAG = FormLoaderTask.class.getSimpleName();

    private FormLoaderListener mStateListener;
    private final SecretKeySpec mSymetricKey;
    private final boolean mReadOnly;

    private final R activity;

    private FECWrapper data;

    public FormLoaderTask(SecretKeySpec symetricKey, boolean readOnly, R activity) {
        this.mSymetricKey = symetricKey;
        this.mReadOnly = readOnly;
        this.activity = activity;
    }

    /**
     * Initialize {@link FormEntryController} with {@link FormDef} from binary or from XML. If given
     * an instance, it will be used to fill the {@link FormDef}.
     */
    @Override
    protected FECWrapper doTaskBackground(Uri... form) {
        FormEntryController fec;
        FormDef fd = null;
        FileInputStream fis;

        Uri theForm = form[0];

        Cursor c = null;
        String formPath = "";
        String formMediaPath = null;
        try {
            //TODO: Selection=? helper
            c = activity.getContentResolver().query(theForm, new String[] {FormsProviderAPI.FormsColumns.FORM_FILE_PATH, FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH}, null, null, null);

            if (!c.moveToFirst()) {
                throw new IllegalArgumentException("Invalid Form URI Provided! No form content found at URI: " + theForm.toString()); 
            }

            formPath = c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH));
            formMediaPath = c.getString(c.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH));
        } finally {
            if (c != null) {
                c.close();
            }
        }

        File formXml = new File(formPath);
        String formHash = FileUtils.getMd5Hash(formXml);
        File formBin = getCachedForm(formHash);

        if (formBin.exists()) {
            // if we have binary, deserialize binary
            Log.i(TAG, "Attempting to load " + formXml.getName() +
                    " from cached file: " + formBin.getAbsolutePath());
            fd = deserializeFormDef(formBin);
            if (fd == null) {
                // some error occured with deserialization. Remove the file, and make a new .formdef
                // from xml
                Log.w(TAG, "Deserialization FAILED!  Deleting cache file: " +
                        formBin.getAbsolutePath());
                formBin.delete();
            }
        }

        // If we couldn't find a cached version, load the form from the XML
        if (fd == null) {
            // no binary, read from xml
            Log.i(TAG, "Attempting to load from: " + formXml.getAbsolutePath());
            try {
                fis = new FileInputStream(formXml);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Error reading XForm file");
            }
            XFormParser.registerHandler("intent", new IntentExtensionParser());
            XFormParser.registerStructuredAction("pollsensor", new PollSensorExtensionParser());
            fd = XFormExtensionUtils.getFormFromInputStream(fis);
            if (fd == null) {
                throw new RuntimeException("Error reading XForm file");
            }
        }
        
        // Try to write the form definition to a cached location
        try {
            serializeFormDef(fd, formPath);
        } catch(Exception e) {
            // The cache is a bonus, so if we can't write it, don't crash, but log 
            // it so we can clean up whatever is preventing the cached version from
            // working
            Logger.log(AndroidLogger.TYPE_RESOURCES, "XForm could not be serialized. Error trace:\n" + ExceptionReportTask.getStackTrace(e));
        }

        fd.exprEvalContext.addFunctionHandler(new CalendaredDateFormatHandler(activity));
        // create FormEntryController from formdef
        FormEntryModel fem = new FormEntryModel(fd);
        fec = new FormEntryController(fem);

        //TODO: Get a reasonable IIF object
        // import existing data into formdef
        if (FormEntryActivity.mInstancePath != null) {
            // This order is important. Import data, then initialize.
            importData(FormEntryActivity.mInstancePath, fec);
            fd.initialize(false, iif);
        } else {
            fd.initialize(true, iif);
        }
        if(mReadOnly) {
            fd.getInstance().getRoot().setEnabled(false);
        }

        // set paths to /sdcard/odk/forms/formfilename-media/
        String formFileName = formXml.getName().substring(0, formXml.getName().lastIndexOf("."));

        // Remove previous forms
        ReferenceManager._().clearSession();

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

            // Set jr://... to point to /sdcard/odk/forms/filename-media/
            ReferenceManager._().addSessionRootTranslator(
                new RootTranslator("jr://images/", "jr://file/forms/" + formFileName + "-media/"));
            ReferenceManager._().addSessionRootTranslator(
                new RootTranslator("jr://audio/", "jr://file/forms/" + formFileName + "-media/"));
            ReferenceManager._().addSessionRootTranslator(
                new RootTranslator("jr://video/", "jr://file/forms/" + formFileName + "-media/"));
        }

        FormController fc = new FormController(fec, mReadOnly);

        data = new FECWrapper(fc);
        return data;
    }

    private boolean importData(String filePath, FormEntryController fec) {
        // convert files into a byte array
        byte[] fileBytes = FileUtils.getFileAsBytes(new File(filePath), mSymetricKey);

        // get the root of the saved and template instances
        TreeElement savedRoot = XFormParser.restoreDataModel(fileBytes, null).getRoot();
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
     * 
     * @param formDef serialized FormDef file
     * @return {@link FormDef} object
     */
    public FormDef deserializeFormDef(File formDef) {
        // TODO: any way to remove reliance on jrsp?

        // need a list of classes that formdef uses
        FileInputStream fis;
        FormDef fd;
        try {
            // create new form def
            fd = new FormDef();
            fis = new FileInputStream(formDef);
            DataInputStream dis = new DataInputStream(new BufferedInputStream(fis));

            // read serialized formdef into new formdef
            fd.readExternal(dis, ApkUtils.getPrototypeFactory(activity));
            dis.close();

        } catch (FileNotFoundException | DeserializationException e) {
            e.printStackTrace();
            fd = null;
        } catch (IOException e) {
            e.printStackTrace();
            fd = null;
        } catch (Throwable e) {
            e.printStackTrace();
            fd = null;
        }

        return fd;
    }

    /**
     * Write the FormDef to the file system as a binary blob.
     * 
     * @param filepath path to the form file
     * @throws IOException 
     */
    @SuppressWarnings("resource")
    public void serializeFormDef(FormDef fd, String filepath) throws IOException {
        // calculate unique md5 identifier for this form
        String hash = FileUtils.getMd5Hash(new File(filepath));
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
                if(outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e){
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

    public void setFormLoaderListener(FormLoaderListener sl) {
        synchronized (this) {
            mStateListener = sl;
        }
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
