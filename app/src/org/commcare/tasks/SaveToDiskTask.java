package org.commcare.tasks;

import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.interfaces.FormSavedListener;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.EncryptionUtils;
import org.commcare.utils.EncryptionUtils.EncryptedFormInformation;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.transport.payload.ByteArrayPayload;
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
    private final Boolean exitAfterSave;
    private final Boolean mMarkCompleted;
    private final int mFormRecordId;
    private final int mFormDefId;
    // The name of the form we are saving
    private final String mRecordName;
    private final String mFormRecordPath;

    private final SecretKeySpec symetricKey;

    public enum SaveStatus {
        SAVED_COMPLETE,
        SAVED_INCOMPLETE,
        SAVE_ERROR,
        INVALID_ANSWER,
        SAVED_AND_EXIT
    }

    public static final int SAVING_TASK_ID = 17;

    public SaveToDiskTask(int formRecordId, int formDefId, String formRecordPath, Boolean saveAndExit, Boolean markCompleted,
                          String updatedName,
                          SecretKeySpec symetricKey, boolean headless) {
        TAG = SaveToDiskTask.class.getSimpleName();

        mFormRecordId = formRecordId;
        mFormDefId = formDefId;
        exitAfterSave = saveAndExit;
        mMarkCompleted = markCompleted;
        mRecordName = updatedName;
        this.symetricKey = symetricKey;
        mFormRecordPath = formRecordPath;

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
        try {
            if (hasInvalidAnswers(mMarkCompleted)) {
                return new ResultAndError<>(SaveStatus.INVALID_ANSWER);
            }
        } catch (XPathException xpe) {
            String cleanedMessage = "An error in your form prevented it from saving: \n" +
                    xpe.getMessage();

            return new ResultAndError<>(SaveStatus.SAVE_ERROR, cleanedMessage);
        }

        FormEntryActivity.mFormController.postProcessInstance();

        try {
            exportData(mMarkCompleted);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return new ResultAndError<>(SaveStatus.SAVE_ERROR,
                    "Something is blocking acesss to the submission file in " + mFormRecordPath);
        } catch (XFormSerializer.UnsupportedUnicodeSurrogatesException e) {
            Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE, "Form contains invalid data encoding\n\n" + ForceCloseLogger.getStackTrace(e));
            return new ResultAndError<>(SaveStatus.SAVE_ERROR,
                    Localization.get("form.entry.save.invalid.unicode", e.getMessage()));
        } catch (IOException e) {
            Logger.log(LogTypes.TYPE_ERROR_STORAGE, "I/O Error when serializing form\n\n" + ForceCloseLogger.getStackTrace(e));
            return new ResultAndError<>(SaveStatus.SAVE_ERROR,
                    "Unable to write xml to " + mFormRecordPath);
        } catch (FormInstanceTransactionException e) {
            e.printStackTrace();
            // Passing exceptions through content providers make error message strings messy.
            String cleanedMessage = e.getMessage().replace("java.lang.IllegalStateException: ", "");
            // Likely a user level issue, so send error to HQ as a app build error
            XPathErrorLogger.INSTANCE.logErrorToCurrentApp(cleanedMessage);

            return new ResultAndError<>(SaveStatus.SAVE_ERROR, cleanedMessage);
        }

        if (exitAfterSave) {
            FormRecord saved = CommCareApplication.instance().getCurrentSessionWrapper().getFormRecord();
            Logger.log(LogTypes.TYPE_FORM_ENTRY,
                    String.format("Form Entry Completed for record with id %s", saved.getInstanceID()));
            return new ResultAndError<>(SaveStatus.SAVED_AND_EXIT);
        } else if (mMarkCompleted) {
            return new ResultAndError<>(SaveStatus.SAVED_COMPLETE);
        } else {
            return new ResultAndError<>(SaveStatus.SAVED_INCOMPLETE);
        }
    }

    /**
     * Update form Record with necessary params
     */
    private void updateFormRecord(SqlStorage<FormRecord> formRecordStorage, boolean incomplete, boolean canEditAfterCompleted)
            throws FormInstanceTransactionException {

        String status;
        if (incomplete || !mMarkCompleted) {
            status = FormRecord.STATUS_INCOMPLETE;
        } else {
            status = FormRecord.STATUS_COMPLETE;
        }

        // update this whether or not the status is complete.
        String canEditWhenComplete = Boolean.toString(canEditAfterCompleted);

        // Insert or update the form instance into the database.

        if (mFormRecordId != -1) {
            // Started with a concrete instance (e.i. by editing an existing
            // form), so just update it.
            try {
                FormRecord formRecord = FormRecord.getFormRecord(formRecordStorage, mFormRecordId);
                formRecord.updateStatus(formRecordStorage, status, mRecordName, canEditWhenComplete);
            } catch (IllegalStateException e) {
                throw new FormInstanceTransactionException(e);
            }
        } else if (mFormDefId != -1) {
            // Started with an empty form or possibly a manually saved form.
            // Try updating, and create a new instance if that fails.

            FormDefRecord formDefRecord = FormDefRecord.getFormDef(mFormDefId);
            String recordName = mRecordName;
            if (recordName == null) {
                recordName = formDefRecord.getDisplayname();
            }

            FormRecord formRecord = CommCareApplication.instance().getCurrentSessionWrapper().getFormRecord();
            formRecord.setFilePath(mFormRecordPath);
            formRecord.setDisplayName(recordName);
            formRecord.setCanEditWhenComplete(canEditWhenComplete);
            formRecord.setStatus(status);
            formRecord.update(formRecordStorage);
        }
    }

    /**
     * Write's the data to the sdcard,
     * In theory we don't have to write to disk, and this is where
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
                EncryptionIO.createFileOutputStream(mFormRecordPath, symetricKey));

        SqlStorage<FormRecord> formRecordStorage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        updateFormRecord(formRecordStorage, true, true);

        if (markCompleted) {
            // now see if it is to be finalized and perhaps update everything...
            boolean canEditAfterCompleted = FormEntryActivity.mFormController.isSubmissionEntireForm();
            boolean isEncrypted = false;

            // build a submission.xml to hold the data being submitted 
            // and (if appropriate) encrypt the files on the side

            // pay attention to the ref attribute of the submission profile...
            payload = FormEntryActivity.mFormController.getSubmissionXml();

            File instanceXml = new File(mFormRecordPath);
            File submissionXml = new File(instanceXml.getParentFile(), "submission.xml");
            // write out submission.xml -- the data to actually submit to aggregate
            writeXmlToStream(payload,
                    EncryptionIO.createFileOutputStream(submissionXml.getAbsolutePath(), symetricKey));

            // see if the form is encrypted and we can encrypt it...
            EncryptedFormInformation formInfo = EncryptionUtils.getEncryptedFormInformation(formRecordStorage, mFormRecordId, mFormDefId, FormEntryActivity.mFormController.getSubmissionMetadata());
            if (formInfo != null) {
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
            // 1. Update the form record database (with status complete).
            // 2. Overwrite the instanceXml with the submission.xml 
            //    and remove the plaintext attachments if encrypting
            updateFormRecord(formRecordStorage, false, canEditAfterCompleted);

            if (!canEditAfterCompleted) {
                // AT THIS POINT, there is no going back.  We are committed
                // to returning "success" (true) whether or not we can 
                // rename "submission.xml" to instanceXml and whether or 
                // not we can delete the plaintext media files.
                //
                // Handle the fall-out for a failed "submission.xml" rename
                // in the InstanceUploader task.  Leftover plaintext media
                // files are handled during form deletion.

                // delete the restore Xml file.
                if (!instanceXml.delete()) {
                    Log.e(TAG, "Error deleting " + instanceXml.getAbsolutePath()
                            + " prior to renaming submission.xml");
                    return;
                }

                // rename the submission.xml to be the instanceXml
                if (!submissionXml.renameTo(instanceXml)) {
                    Log.e(TAG, "Error renaming submission.xml to " + instanceXml.getAbsolutePath());
                    return;
                }

                // if encrypted, delete all plaintext files
                // (anything not named instanceXml or anything not ending in .enc)
                if (isEncrypted) {
                    if (!EncryptionUtils.deletePlaintextFiles(instanceXml)) {
                        Log.e(TAG, "Error deleting plaintext files for " + instanceXml.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void writeXmlToStream(ByteArrayPayload payload, OutputStream output) throws IOException {
        try {
            InputStream is = payload.getPayloadStream();
            StreamsUtil.writeFromInputToOutput(is, output);
        } finally {
            output.close();
        }
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
     */
    private boolean hasInvalidAnswers(boolean markCompleted) {
        FormIndex i = FormEntryActivity.mFormController.getFormIndex();
        FormEntryActivity.mFormController.jumpToIndex(FormIndex.createBeginningOfFormIndex());

        int event;
        while ((event =
                FormEntryActivity.mFormController.stepToNextEvent(FormEntryController.STEP_INTO_GROUP)) != FormEntryController.EVENT_END_OF_FORM) {
            if (event == FormEntryController.EVENT_QUESTION) {
                int saveStatus =
                        FormEntryActivity.mFormController.checkCurrentQuestionConstraint();
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
