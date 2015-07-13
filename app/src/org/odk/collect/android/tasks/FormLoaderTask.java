package org.odk.collect.android.tasks;

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

import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.dalvik.odk.provider.FormsProviderAPI;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.utils.InstanceLoader;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.RootTranslator;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.PrototypeFactory;
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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

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

        // calculate unique md5 identifier for this form
        String hash = FileUtils.getMd5Hash(new File(formPath));
        File formDefFile = new File(Collect.CACHE_PATH + "/" + hash + ".formdef");

        byte[] fileBytes = null;
        if (FormEntryActivity.mInstancePath != null) {
            fileBytes = FileUtils.getFileAsBytes(new File(FormEntryActivity.mInstancePath), mSymetricKey);
        }


        File formXml = new File(formPath);
        String formHash = FileUtils.getMd5Hash(formXml);
        File formBin = new File(Collect.CACHE_PATH + "/" + formHash + ".formdef");


        PrototypeFactory protoFactory = ApkUtils.getPrototypeFactory(context);
        FormEntryController fec;
        try {
            fec = InstanceLoader.loadInstance(formMediaPath, formDefFile, fileBytes, formBin, formXml, mReadOnly, protoFactory, iif);
        } catch (Exception e) {
            fec = null;
        }

        FormController fc = new FormController(fec, mReadOnly);

        data = new FECWrapper(fc);
        return data;
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
