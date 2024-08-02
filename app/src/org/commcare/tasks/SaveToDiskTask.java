package org.commcare.tasks;

import org.commcare.activities.FormEntryActivity;
import org.commcare.interfaces.FormSavedListener;
import org.commcare.tasks.templates.CommCareTask;
import org.javarosa.core.model.FormDef;
import org.javarosa.form.api.FormEntryController;

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
            FormSaveHelper.clearInstance();
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

    @Override
    protected void onCancelled() {
        super.onCancelled();
        FormSaveHelper.clearInstance();
    }
}
