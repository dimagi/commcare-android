package org.odk.collect.android.tasks;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.ExceptionReportTask;
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
import org.javarosa.xform.parse.XFormParseException;
import org.javarosa.xform.parse.XFormParser;
import org.javarosa.xform.util.XFormUtils;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.jr.extensions.CalendaredDateFormatHandler;
import org.odk.collect.android.jr.extensions.IntentExtensionParser;
import org.odk.collect.android.jr.extensions.PollSensorExtensionParser;
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
public class FormLoaderTask extends AsyncTask<Uri, String, FormLoaderTask.FECWrapper> {
    public static InstanceInitializationFactory iif;
    private final static String TAG = FormLoaderTask.class.getSimpleName();

    private FormLoaderListener mStateListener;
    private String mErrorMsg;
    private final SecretKeySpec mSymetricKey;
    private final boolean mReadOnly;

    private final Context context;

    public FormLoaderTask(Context context) {
        this(context, null, false);
    }

    public FormLoaderTask(Context context, SecretKeySpec symetricKey, boolean readOnly) {
        this.context = context;
        this.mSymetricKey = symetricKey;
        this.mReadOnly = readOnly;
    }

    private FECWrapper data;

    /**
     * Initialize {@link FormEntryController} with {@link FormDef} from binary or from XML. If given
     * an instance, it will be used to fill the {@link FormDef}.
     */
    @Override
    protected FECWrapper doInBackground(Uri... form) {
        FormEntryController fec;
        FormDef fd = null;
        FileInputStream fis;
        mErrorMsg = null;

        Uri theForm = form[0];

        Cursor c = null;
        String formPath = "";
        String formMediaPath = null;
        try {
            //TODO: Selection=? helper
            c = context.getContentResolver().query(theForm, new String[] {FormsProviderAPI.FormsColumns.FORM_FILE_PATH, FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH}, null, null, null);

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
        File formBin = new File(Collect.CACHE_PATH + "/" + formHash + ".formdef");

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
            try {
                Log.i(TAG, "Attempting to load from: " + formXml.getAbsolutePath());
                fis = new FileInputStream(formXml);
                XFormParser.registerHandler("intent", new IntentExtensionParser());
                XFormParser.registerStructuredAction("pollsensor", new PollSensorExtensionParser());
                fd = XFormUtils.getFormFromInputStream(fis);
                if (fd == null) {
                    mErrorMsg = "Error reading XForm file";
                }
            } catch (XFormParseException | FileNotFoundException e) {
                mErrorMsg = e.getMessage();
                e.printStackTrace();
            } catch (Exception e) {
                mErrorMsg = e.getMessage();
                e.printStackTrace();
            }
        }

        //If we errored out, report back the issue
        if (mErrorMsg != null) {
            return null;
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

        fd.exprEvalContext.addFunctionHandler(new CalendaredDateFormatHandler(context));
        // create FormEntryController from formdef
        FormEntryModel fem = new FormEntryModel(fd);
        fec = new FormEntryController(fem);

        //TODO: Get a reasonable IIF object
        //iif = something
        try {
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
        } catch (RuntimeException e) {
            e.printStackTrace();
            mErrorMsg = e.getMessage();
            return null;
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
            fd.readExternal(dis, ApkUtils.getPrototypeFactory(context));
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
        File formDef = new File(Collect.CACHE_PATH + "/" + hash + ".formdef");

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

    @Override
    protected void onPostExecute(FECWrapper wrapper) {
        synchronized (this) {
            if (mStateListener != null) {
                if (wrapper == null) {
                    mStateListener.loadingError(mErrorMsg);
                } else {
                    mStateListener.loadingComplete(wrapper.getController());
                }
            }
        }
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

        protected FormController getController() {
            return controller;
        }

        protected void free() {
            controller = null;
        }
    }
}
