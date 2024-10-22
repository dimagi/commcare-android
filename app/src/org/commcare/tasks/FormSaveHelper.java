package org.commcare.tasks;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.components.ImageCaptureProcessing;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.logging.XPathErrorLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.encryption.EncryptionIO;
import org.commcare.util.FormMetaIndicatorUtil;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.javarosa.core.io.StreamsUtil;
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

public class FormSaveHelper {

    private static FormSaveHelper singletonRunningInstance = null;
    private static final Object lock = new Object();

    private static final String TAG = FormSaveHelper.class.getSimpleName();

    private final Boolean mExitAfterSave;
    private final Boolean mMarkCompleted;
    private final int mFormRecordId;
    private final int mFormDefId;
    // The name of the form we are saving
    private final String mRecordName;
    private final String mFormRecordPath;

    private final SecretKeySpec mSymetricKey;

    private FormSaveHelper(int formRecordId, int formDefId, Boolean exitAfterSave, Boolean markCompleted,
            String recordName, SecretKeySpec symetricKey, String formRecordPath) {
        mFormRecordId = formRecordId;
        mFormDefId = formDefId;
        mMarkCompleted = markCompleted;
        mExitAfterSave = exitAfterSave;
        mRecordName = recordName;
        mFormRecordPath = formRecordPath;
        mSymetricKey = symetricKey;
    }

    public static FormSaveHelper getNewInstance(int formRecordId, int formDefId, Boolean exitAfterSave, Boolean markCompleted,
            String recordName, SecretKeySpec symetricKey, String formRecordPath){
        synchronized (lock) {
            if (singletonRunningInstance == null) {
                singletonRunningInstance = new FormSaveHelper(formRecordId, formDefId, exitAfterSave,
                        markCompleted, recordName, symetricKey, formRecordPath);
                return singletonRunningInstance;
            } else {
                throw new IllegalStateException("An instance of " + TAG + " already exists.");
            }
        }
    }

    public static void clearInstance() {
        synchronized (lock) {
            singletonRunningInstance = null;
        }
    }

    public ResultAndError<SaveToDiskTask.SaveStatus> saveForm() {
        long time = System.currentTimeMillis();
        try {
            if (hasInvalidAnswers(mMarkCompleted)) {
                return new ResultAndError<>(SaveToDiskTask.SaveStatus.INVALID_ANSWER);
            }
        } catch (XPathException xpe) {
            String cleanedMessage = "An error in your form prevented it from saving: \n" +
                    xpe.getMessage();
            return new ResultAndError<>(SaveToDiskTask.SaveStatus.SAVE_ERROR, cleanedMessage);
        }

        FormEntryActivity.mFormController.postProcessInstance();

        try {
            exportData(mMarkCompleted);
        } catch (FileNotFoundException e) {
            return new ResultAndError<>(SaveToDiskTask.SaveStatus.SAVE_ERROR,
                    "Something is blocking acesss to the submission file in " + mFormRecordPath);
        } catch (XFormSerializer.UnsupportedUnicodeSurrogatesException e) {
            Logger.log(
                    LogTypes.TYPE_ERROR_CONFIG_STRUCTURE, "Form contains invalid data encoding\n\n" + ForceCloseLogger.getStackTrace(e));
            return new ResultAndError<>(SaveToDiskTask.SaveStatus.SAVE_ERROR,
                    Localization.get("form.entry.save.invalid.unicode", e.getMessage()));
        } catch (IOException e) {
            Logger.log(LogTypes.TYPE_ERROR_STORAGE, "I/O Error when serializing form\n\n" + ForceCloseLogger.getStackTrace(e));
            return new ResultAndError<>(SaveToDiskTask.SaveStatus.SAVE_ERROR,
                    "Unable to write xml to " + mFormRecordPath);
        } catch (FormInstanceTransactionException e) {
            // Passing exceptions through content providers make error message strings messy.
            String cleanedMessage = e.getMessage().replace("java.lang.IllegalStateException: ", "");
            // Likely a user level issue, so send error to HQ as a app build error
            XPathErrorLogger.INSTANCE.logErrorToCurrentApp(cleanedMessage);
            return new ResultAndError<>(SaveToDiskTask.SaveStatus.SAVE_ERROR, cleanedMessage);
        }

        if (mMarkCompleted) {
            FormEntryActivity.mFormController.markCompleteFormAsSaved();
        }

        logFormSave(mExitAfterSave);
        if (mExitAfterSave) {
            return new ResultAndError<>(SaveToDiskTask.SaveStatus.SAVED_AND_EXIT);
        } else if (mMarkCompleted) {
            return new ResultAndError<>(SaveToDiskTask.SaveStatus.SAVED_COMPLETE);
        } else {
            return new ResultAndError<>(SaveToDiskTask.SaveStatus.SAVED_INCOMPLETE);
        }
    }

    private void logFormSave(boolean exit) {
        FormRecord saved = CommCareApplication.instance().getCurrentSessionWrapper().getFormRecord();
        String log = String.format("Form Entry Completed: Record with id %s was saved as %s", saved.getInstanceID(), mMarkCompleted ? "complete" : "incomplete");
        if(exit){
            log += " with user exiting";
        }
        Logger.log(LogTypes.TYPE_FORM_ENTRY, log);
    }

    /**
     * Update form Record with necessary params
     */
    private void updateFormRecord(SqlStorage<FormRecord> formRecordStorage, boolean incomplete)
            throws FormInstanceTransactionException {

        String status;
        if (incomplete || !mMarkCompleted) {
            status = FormRecord.STATUS_INCOMPLETE;
        } else {
            status = FormRecord.STATUS_COMPLETE;
        }

        // Insert or update the form instance into the database.
        FormRecord formRecord = null;
        String recordName = mRecordName;
        if (mFormRecordId != -1) {
            // We started with a concrete instance (i.e. by editing an existing form)
            formRecord = FormRecord.getFormRecord(formRecordStorage, mFormRecordId);
        } else if (mFormDefId != -1) {
            // We started with an empty form or possibly a manually saved form
            formRecord = CommCareApplication.instance().getCurrentSessionWrapper().getFormRecord();
            formRecord.setFilePath(mFormRecordPath);
            if (recordName == null) {
                FormDefRecord formDefRecord = FormDefRecord.getFormDef(
                        CommCareApplication.instance().getAppStorage(FormDefRecord.class), mFormDefId);
                recordName = formDefRecord.getDisplayName();
            }
        }

        if (formRecord != null) {
            try {
                formRecord.setDisplayName(recordName);
                String caseName = FormMetaIndicatorUtil.getPragma(
                        FormMetaIndicatorUtil.FORM_DESCRIPTOR,
                        FormEntryActivity.mFormController.getFormDef(),
                        TreeReference.rootRef());
                formRecord.setDescriptor(caseName);
                formRecord.updateStatus(formRecordStorage, status);
            } catch (IllegalStateException e) {
                throw new FormInstanceTransactionException(e);
            }
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

        FormInstance dataModel = FormEntryActivity.mFormController.getInstance();
        XFormSerializingVisitor serializer = new XFormSerializingVisitor(markCompleted);
        ByteArrayPayload payload = (ByteArrayPayload)serializer.createSerializedPayload(dataModel);

        writeXmlToStream(payload,
                EncryptionIO.createFileOutputStream(mFormRecordPath, mSymetricKey));

        SqlStorage<FormRecord> formRecordStorage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        updateFormRecord(formRecordStorage, true);

        if (markCompleted) {
            payload = FormEntryActivity.mFormController.getSubmissionXml();
            File instanceXml = new File(mFormRecordPath);
            File submissionXml = new File(instanceXml.getParentFile(), "submission.xml");
            // write out submission.xml -- the data to actually submit to aggregate
            writeXmlToStream(payload,
                    EncryptionIO.createFileOutputStream(submissionXml.getAbsolutePath(), mSymetricKey));

            // Set this record's status to COMPLETE
            updateFormRecord(formRecordStorage, false);

            // delete the restore Xml file.
            if (!instanceXml.delete()) {
                Logger.log(LogTypes.TYPE_MAINTENANCE,
                        "Error deleting " + instanceXml.getAbsolutePath()
                                + " prior to renaming submission.xml");
                return;
            }

            // rename the submission.xml to be the instanceXml
            if (!submissionXml.renameTo(instanceXml)) {
                Logger.log(LogTypes.TYPE_MAINTENANCE,
                        "Error renaming submission.xml to " + instanceXml.getAbsolutePath());
            }

            String rawDirPath = ImageCaptureProcessing.getRawDirectoryPath(instanceXml.getParent());
            if(!FileUtil.deleteFileOrDir(rawDirPath)){
                Logger.log(LogTypes.TYPE_MAINTENANCE, "Error deleting raw dir at path " + rawDirPath);
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

    /**
     * Goes through the entire form to make sure all entered answers comply
     * with their constraints.  Constraints are ignored on 'jump to', so
     * answers can be outside of constraints. We don't allow saving to disk,
     * though, until all answers conform to their constraints/requirements.
     */
    private boolean hasInvalidAnswers(boolean markCompleted) {
        FormController formController = FormEntryActivity.mFormController;
        FormIndex currentFormIndex = FormIndex.createBeginningOfFormIndex();
        int event;
        while ((event = formController.getEvent(currentFormIndex)) != FormEntryController.EVENT_END_OF_FORM) {
            if (event == FormEntryController.EVENT_QUESTION) {
                int saveStatus = formController.checkCurrentQuestionConstraint(currentFormIndex);
                if (markCompleted &&
                        (saveStatus == FormEntryController.ANSWER_REQUIRED_BUT_EMPTY ||
                                saveStatus == FormEntryController.ANSWER_CONSTRAINT_VIOLATED)) {
                    formController.jumpToIndex(currentFormIndex);
                    return true;
                }
            }
            currentFormIndex = formController.getNextFormIndex(currentFormIndex, FormEntryController.STEP_INTO_GROUP, true);
        }
        return false;
    }

    private static class FormInstanceTransactionException extends Exception {
        FormInstanceTransactionException(Throwable throwable) {
            super(throwable);
        }
    }
}
