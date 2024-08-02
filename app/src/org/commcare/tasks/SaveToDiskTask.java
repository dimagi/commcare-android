package org.commcare.tasks;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.components.ImageCaptureProcessing;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.interfaces.FormSavedListener;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.util.FormMetaIndicatorUtil;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
import org.javarosa.form.api.FormController;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.model.xform.XFormSerializingVisitor;
import org.javarosa.xform.util.XFormSerializer;
import org.javarosa.xpath.XPathException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.crypto.spec.SecretKeySpec;

/**
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SaveToDiskTask extends
        CommCareTask<Void, String, ResultAndError<SaveToDiskTask.SaveStatus>, FormEntryActivity> {
    // callback to run upon saving
    private FormSavedListener mSavedListener;
    private final FormSaveHelper mFormSaveHelper;


    public enum SaveStatus {
        SAVED_COMPLETE,
        SAVED_INCOMPLETE,
        SAVE_ERROR,
        INVALID_ANSWER,
        SAVED_AND_EXIT
    }

    public static final int SAVING_TASK_ID = 17;

    public SaveToDiskTask(FormSaveHelper formSaveHelper, boolean headless) {
        TAG = SaveToDiskTask.class.getSimpleName();
        mFormSaveHelper = formSaveHelper;

        if (headless) {
            this.taskId = -1;
            //Don't block on the UI thread if there's no available screen to connect to
            this.setConnectionTimeout(0);
        } else {
            this.taskId = SAVING_TASK_ID;
        }
    }

    /**
     * Initialize {@link FormEntryController} with {@link FormDef} from binary or from XML. If given
     * an instance, it will be used to fill the {@link FormDef}.
     */
    @Override
    protected ResultAndError<SaveStatus> doTaskBackground(Void... nothing) {
        return mFormSaveHelper.saveForm();
    }



    @Override
    protected void onPostExecute(ResultAndError<SaveStatus> result) {
        super.onPostExecute(result);
        synchronized (this) {
            if (mSavedListener != null) {
                if (result == null) {
                    mSavedListener.savingComplete(SaveStatus.SAVE_ERROR, "Unknown Error");
                } else {
                    mSavedListener.savingComplete(result.data, result.errorMessage);
                }
            }
        }
    }

    @Override
    protected void deliverResult(FormEntryActivity receiver, ResultAndError<SaveStatus> result) {
    }

    @Override
    protected void deliverUpdate(FormEntryActivity receiver, String... update) {
    }

    @Override
    protected void deliverError(FormEntryActivity receiver, Exception e) {
    }

    public void setFormSavedListener(FormSavedListener fsl) {
        synchronized (this) {
            mSavedListener = fsl;
        }
    }

}
