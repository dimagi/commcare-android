package org.commcare.tasks;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.commcare.activities.FormEntryActivity;
import org.commcare.interfaces.FormSavedListener;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.provider.FormsProviderAPI.FormsColumns;
import org.commcare.provider.InstanceProviderAPI;
import org.commcare.provider.InstanceProviderAPI.InstanceColumns;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.EncryptionUtils;
import org.commcare.utils.EncryptionUtils.EncryptedFormInformation;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.model.xform.XFormSerializingVisitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import javax.crypto.spec.SecretKeySpec;

/**
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SaveToDiskTask extends
        CommCareTask<Void, String, ResultAndError<SaveToDiskTask.SaveStatus>, FormEntryActivity> {
    // callback to run upon saving
    private FormSavedListener mSavedListener;
    private final Boolean exitAfterSave;
    private final Boolean mMarkCompleted;
    // URI to the thing we are saving
    private Uri mUri;
    // The name of the form we are saving
    private final String mInstanceName;
    private final Context context;
    // URI to the table we are saving to
    private final Uri instanceContentUri;

    private final SecretKeySpec symetricKey;

    public enum SaveStatus {
        SAVED_COMPLETE,
        SAVED_INCOMPLETE,
        SAVE_ERROR,
        INVALID_ANSWER,
        SAVED_AND_EXIT
    }

    public static final int SAVING_TASK_ID = 17;

    public SaveToDiskTask(Uri mUri, Boolean saveAndExit, Boolean markCompleted,
                          String updatedName, Context context, Uri instanceContentUri,
                          SecretKeySpec symetricKey, boolean headless) {
        TAG = SaveToDiskTask.class.getSimpleName();

        this.mUri = mUri;
        exitAfterSave = saveAndExit;
        mMarkCompleted = markCompleted;
        mInstanceName = updatedName;
        this.context = context;
        this.instanceContentUri = instanceContentUri;
        this.symetricKey = symetricKey;

        if (headless) {
            this.taskId = -1;
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
        if (hasInvalidAnswers(mMarkCompleted, DeveloperPreferences.shouldFireTriggersOnSave())) {
            return new ResultAndError<>(SaveStatus.INVALID_ANSWER);
        }

        FormEntryActivity.mFormController.postProcessInstance();

        try {
            exportData(mMarkCompleted);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return new ResultAndError<>(SaveStatus.SAVE_ERROR,
                    "Something is blocking acesss to the submission file in " + FormEntryActivity.mInstancePath);
        } catch(UnsupportedEncodingException uee) {
            Logger.exception(uee);
            Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Form contains invalid data encoding");
            return new ResultAndError<>(SaveStatus.SAVE_ERROR,
                    "Form contains invalidly encoded text! Unable to save contents.");
        }  catch (IOException e) {
            Logger.log(AndroidLogger.TYPE_ERROR_STORAGE, "I/O Error when serializing form");
            return new ResultAndError<>(SaveStatus.SAVE_ERROR,
                    "Unable to write xml to " + FormEntryActivity.mInstancePath);
        } catch (FormInstanceTransactionException e) {
            // TODO PLM: send this error to HQ as a app build error, most
            // likely a user level issue.
            e.printStackTrace();
            // Passing exceptions through content providers make error message strings messy.
            String cleanedMessage = e.getMessage().replace("java.lang.IllegalStateException: ", "");
            return new ResultAndError<>(SaveStatus.SAVE_ERROR, cleanedMessage);
        }

        if (exitAfterSave) {
            return new ResultAndError<>(SaveStatus.SAVED_AND_EXIT);
        } else if (mMarkCompleted) {
            return new ResultAndError<>(SaveStatus.SAVED_COMPLETE);
        } else {
            return new ResultAndError<>(SaveStatus.SAVED_INCOMPLETE);
        }
    }

    /**
     * Update or create a new entry in the form table for the
     */
    private void updateInstanceDatabase(boolean incomplete, boolean canEditAfterCompleted)
            throws FormInstanceTransactionException {
        ContentValues values = new ContentValues();
        if (mInstanceName != null) {
            values.put(InstanceColumns.DISPLAY_NAME, mInstanceName);
        }

        if (incomplete || !mMarkCompleted) {
            values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_INCOMPLETE);
        } else {
            values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_COMPLETE);
        }
        // update this whether or not the status is complete.
        values.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE, Boolean.toString(canEditAfterCompleted));

        // Insert or update the form instance into the database.
        String resolverType = context.getContentResolver().getType(mUri);
        if (InstanceColumns.CONTENT_ITEM_TYPE.equals(resolverType)) {
            // Started with a concrete instance (e.i. by editing an existing
            // form), so just update it.
            try {
                context.getContentResolver().update(mUri, values, null, null);
            } catch (IllegalStateException e) {
                throw new FormInstanceTransactionException(e);
            }
        } else if (FormsColumns.CONTENT_ITEM_TYPE.equals(resolverType)) {
            // Started with an empty form or possibly a manually saved form.
            // Try updating, and create a new instance if that fails.
            String[] whereArgs = {FormEntryActivity.mInstancePath};
            int rowsUpdated;
            try {
                rowsUpdated = context.getContentResolver().update(instanceContentUri, values,
                        InstanceColumns.INSTANCE_FILE_PATH + "=?", whereArgs);
            } catch (IllegalStateException e) {
                throw new FormInstanceTransactionException(e);
            }
            if (rowsUpdated == 0) {
                // Form instance didn't exist in the table, so create it.
                Log.e(TAG, "No instance found, creating");
                Cursor c = null;
                try {
                    // grab the first entry in the instance table for the form
                    c = context.getContentResolver().query(mUri, null, null, null, null);
                    c.moveToFirst();
                    // copy data out of that entry, into the entry we are creating
                    values.put(InstanceColumns.JR_FORM_ID,
                            c.getString(c.getColumnIndex(FormsColumns.JR_FORM_ID)));
                    values.put(InstanceColumns.SUBMISSION_URI,
                            c.getString(c.getColumnIndex(FormsColumns.SUBMISSION_URI)));

                    if (mInstanceName == null) {
                        values.put(InstanceColumns.DISPLAY_NAME,
                                c.getString(c.getColumnIndex(FormsColumns.DISPLAY_NAME)));
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                values.put(InstanceColumns.INSTANCE_FILE_PATH, FormEntryActivity.mInstancePath);
                try {
                    mUri = context.getContentResolver().insert(instanceContentUri, values);
                } catch (IllegalStateException e) {
                    throw new FormInstanceTransactionException(e);
                }
            } else if (rowsUpdated == 1) {
                Log.i(TAG, "Instance already exists, updating");
            } else{
                Log.w(TAG, "Updated more than one entry, that's not good");
            }
        }
    }

    /**
     * Write's the data to the sdcard, and updates the instances content
     * provider. In theory we don't have to write to disk, and this is where
     * you'd add other methods.
     *
     * @throws IOException                      Issue serializing form and
     *                                          storing to filesystem
     * @throws FormInstanceTransactionException Issue performing transactions
     *                                          associated with form saving,
     *                                          like case updates and updating
     *                                          the associated form record
     */
    private void exportData(boolean markCompleted)
            throws IOException, FormInstanceTransactionException {
        ByteArrayPayload payload;
        // assume no binary data inside the model.
        FormInstance datamodel = FormEntryActivity.mFormController.getInstance();
        XFormSerializingVisitor serializer = new XFormSerializingVisitor(markCompleted);
        payload = (ByteArrayPayload)serializer.createSerializedPayload(datamodel);

        writeXmlToStream(payload,
                EncryptionIO.createFileOutputStream(FormEntryActivity.mInstancePath, symetricKey));

        // update the mUri. We've saved the reloadable instance, so update status...
        updateInstanceDatabase(true, true);

        if ( markCompleted ) {
            // now see if it is to be finalized and perhaps update everything...
            boolean canEditAfterCompleted = FormEntryActivity.mFormController.isSubmissionEntireForm();
            boolean isEncrypted = false;

            // build a submission.xml to hold the data being submitted 
            // and (if appropriate) encrypt the files on the side

            // pay attention to the ref attribute of the submission profile...
            payload = FormEntryActivity.mFormController.getSubmissionXml();

            File instanceXml = new File(FormEntryActivity.mInstancePath);
            File submissionXml = new File(instanceXml.getParentFile(), "submission.xml");
            // write out submission.xml -- the data to actually submit to aggregate
            writeXmlToStream(payload,
                    EncryptionIO.createFileOutputStream(submissionXml.getAbsolutePath(), symetricKey));

            // see if the form is encrypted and we can encrypt it...
            EncryptedFormInformation formInfo = EncryptionUtils.getEncryptedFormInformation(mUri, FormEntryActivity.mFormController.getSubmissionMetadata(), context, instanceContentUri);
            if ( formInfo != null ) {
                // if we are encrypting, the form cannot be reopened afterward
                canEditAfterCompleted = false;
                // and encrypt the submission (this is a one-way operation)...
                if (!EncryptionUtils.generateEncryptedSubmission(instanceXml, submissionXml, formInfo)) {
                    throw new RuntimeException("Unable to encrypt form submission.");
                }
                isEncrypted = true;
            }

            // At this point, we have:
            // 1. the saved original instanceXml, 
            // 2. all the plaintext attachments
            // 2. the submission.xml that is the completed xml (whether encrypting or not)
            // 3. all the encrypted attachments if encrypting (isEncrypted = true).
            //
            // NEXT:
            // 1. Update the instance database (with status complete).
            // 2. Overwrite the instanceXml with the submission.xml 
            //    and remove the plaintext attachments if encrypting
            updateInstanceDatabase(false, canEditAfterCompleted);

            if (  !canEditAfterCompleted ) {
                // AT THIS POINT, there is no going back.  We are committed
                // to returning "success" (true) whether or not we can 
                // rename "submission.xml" to instanceXml and whether or 
                // not we can delete the plaintext media files.
                //
                // Handle the fall-out for a failed "submission.xml" rename
                // in the InstanceUploader task.  Leftover plaintext media
                // files are handled during form deletion.

                // delete the restore Xml file.
                if ( !instanceXml.delete() ) {
                    Log.e(TAG, "Error deleting " + instanceXml.getAbsolutePath()
                            + " prior to renaming submission.xml");
                    return;
                }

                // rename the submission.xml to be the instanceXml
                if ( !submissionXml.renameTo(instanceXml) ) {
                    Log.e(TAG, "Error renaming submission.xml to " + instanceXml.getAbsolutePath());
                    return;
                }

                // if encrypted, delete all plaintext files
                // (anything not named instanceXml or anything not ending in .enc)
                if ( isEncrypted ) {
                    if ( !EncryptionUtils.deletePlaintextFiles(instanceXml) ) {
                        Log.e(TAG, "Error deleting plaintext files for " + instanceXml.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void writeXmlToStream(ByteArrayPayload payload, OutputStream output) throws IOException {
        InputStream is = payload.getPayloadStream();
        StreamsUtil.writeFromInputToOutput(is, output);
        output.close();
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

    /**
     * Goes through the entire form to make sure all entered answers comply
     * with their constraints.  Constraints are ignored on 'jump to', so
     * answers can be outside of constraints. We don't allow saving to disk,
     * though, until all answers conform to their constraints/requirements.
     * @param fireTriggerables re-fire the triggers associated with the
     *                         question when checking its constraints?
     */
    private boolean hasInvalidAnswers(boolean markCompleted, boolean fireTriggerables) {
        FormIndex i = FormEntryActivity.mFormController.getFormIndex();
        FormEntryActivity.mFormController.jumpToIndex(FormIndex.createBeginningOfFormIndex());

        int event;
        if (!fireTriggerables) {
            Logger.log(AndroidLogger.TYPE_FORM_ENTRY, "Saving form without firing triggers.");
        }
        while ((event =
            FormEntryActivity.mFormController.stepToNextEvent(FormEntryController.STEP_INTO_GROUP)) != FormEntryController.EVENT_END_OF_FORM) {
            if (event == FormEntryController.EVENT_QUESTION) {
                int saveStatus;
                if (fireTriggerables) {
                    saveStatus =
                            FormEntryActivity.mFormController
                                    .answerQuestion(FormEntryActivity.mFormController.getQuestionPrompt()
                                            .getAnswerValue());
                } else {
                    saveStatus =
                            FormEntryActivity.mFormController.checkCurrentQuestionConstraint();
                }
                if (markCompleted &&
                        (saveStatus == FormEntryController.ANSWER_REQUIRED_BUT_EMPTY ||
                                saveStatus == FormEntryController.ANSWER_CONSTRAINT_VIOLATED)) {

                    return true;
                }
            }
        }

        FormEntryActivity.mFormController.jumpToIndex(i);
        return false;
    }

    private static class FormInstanceTransactionException extends Exception {
        FormInstanceTransactionException(Throwable throwable) {
            super(throwable);
        }
    }
}
