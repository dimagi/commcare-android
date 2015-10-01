package org.commcare.android.tasks;

import android.content.SharedPreferences;
import android.os.AsyncTask;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.entity.mime.MultipartEntity;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.UserStorageClosedException;
import org.javarosa.core.model.User;
import org.commcare.android.io.DataSubmissionEntity;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.AndroidLogSerializer;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.android.javarosa.DeviceReportWriter;
import org.commcare.android.mime.EncryptedFileBody;
import org.commcare.android.models.notifications.MessageTag;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.android.tasks.LogSubmissionTask.LogSubmitOutcomes;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author ctsims
 */
public class LogSubmissionTask extends AsyncTask<Void, Long, LogSubmitOutcomes> implements DataSubmissionListener {

    //Stole from the process and send task. See if we can unify a lot of this behavior
    private static final long SUBMISSION_BEGIN = 16;
    private static final long SUBMISSION_START = 32;
    private static final long SUBMISSION_NOTIFY = 64;
    private static final long SUBMISSION_DONE = 128;

    public enum LogSubmitOutcomes implements MessageTag {
        /**
         * Logs successfully submitted
         **/
        Submitted("notification.logger.submitted"),

        /**
         * Logs saved, but not actually submitted
         **/
        Serialized("notification.logger.serialized"),

        /**
         * Something went wrong
         **/
        Error("notification.logger.error");

        LogSubmitOutcomes(String root) {
            this.root = root;
        }

        private final String root;

        public String getLocaleKeyBase() {
            return root;
        }

        public String getCategory() {
            return "log_submission";
        }
    }

    private boolean serializeCurrentLogs = false;
    private final DataSubmissionListener listener;
    private final String submissionUrl;

    public LogSubmissionTask(boolean serializeCurrentLogs, DataSubmissionListener listener, String submissionUrl) {
        this.serializeCurrentLogs = serializeCurrentLogs;
        this.listener = listener;
        this.submissionUrl = submissionUrl;
    }

    @Override
    protected LogSubmitOutcomes doInBackground(Void... params) {
        try {
            SqlStorage<DeviceReportRecord> storage = CommCareApplication._().getUserStorage(DeviceReportRecord.class);

            if (serializeCurrentLogs && !serializeLogs(storage)) {
                return LogSubmitOutcomes.Error;
            }

            // See how many we have pending to submit.
            int numberOfLogsToSubmit = storage.getNumRecords();
            if (numberOfLogsToSubmit == 0) {
                return LogSubmitOutcomes.Submitted;
            }

            // Signal to the listener that we're ready to submit
            this.beginSubmissionProcess(numberOfLogsToSubmit);

            ArrayList<Integer> submittedSuccesfullyIds = new ArrayList<>();
            ArrayList<DeviceReportRecord> submittedSuccesfully = new ArrayList<>();
            submitReports(storage, submittedSuccesfullyIds, submittedSuccesfully);

            if (!removeLocalReports(storage, submittedSuccesfullyIds, submittedSuccesfully)) {
                return LogSubmitOutcomes.Serialized;
            }

            return checkSubmissionResult(numberOfLogsToSubmit, submittedSuccesfully);
        } catch (UserStorageClosedException e) {
            // The user database closed on us
            return LogSubmitOutcomes.Error;
        }
    }

    private boolean serializeLogs(SqlStorage<DeviceReportRecord> storage) {
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();

        //update the last recorded record
        settings.edit().putLong(CommCarePreferences.LOG_LAST_DAILY_SUBMIT, new Date().getTime()).commit();

        DeviceReportRecord record;
        try {
            record = DeviceReportRecord.generateNewRecordStub();
        } catch (SessionUnavailableException e) {
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "User database closed while trying to submit logs");
            return false;
        }

        //Ok, so first, we're going to write the logs to disk in an encrypted file
        try {
            DeviceReportWriter reporter;
            try {
                //Create a report writer
                reporter = new DeviceReportWriter(record);
            } catch (IOException e) {
                //TODO: Bad local file (almost certainly). Throw a better message!
                e.printStackTrace();
                return false;
            }

            //Add the logs as the primary payload
            AndroidLogSerializer serializer = new AndroidLogSerializer(CommCareApplication._().getGlobalStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class));
            reporter.addReportElement(serializer);

            //serialize logs
            reporter.write();

            //Write the record for where the logs are now saved to.
            storage.write(record);

            //The logs are saved and recorded, so we can feel safe clearing the logs we serialized.
            serializer.purge();
        } catch (Exception e) {
            //Bad times!
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void submitReports(SqlStorage<DeviceReportRecord> storage,
                               ArrayList<Integer> submittedSuccesfullyIds,
                               ArrayList<DeviceReportRecord> submittedSuccesfully) {
        int index = 0;
        for (DeviceReportRecord slr : storage) {
            try {
                if (submit(slr, index)) {
                    submittedSuccesfullyIds.add(slr.getID());
                    submittedSuccesfully.add(slr);
                }
                index++;
            } catch (Exception e) {

            }
        }
    }

    private boolean submit(DeviceReportRecord slr, int index) {
        //Get our file pointer
        File f = new File(slr.getFilePath());

        // Bad (Empty) record. Wipe
        if (f.length() == 0) {
            return true;
        }

        //signal that it's time to start submitting the file
        this.startSubmission(index, f.length());
        HttpRequestGenerator generator;
        User user;
        try {
            user = CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException e) {
            // lost the session, so report failed submission
            return false;
        }

        if (User.TYPE_DEMO.equals(user.getUserType())) {
            generator = new HttpRequestGenerator();
        } else {
            generator = new HttpRequestGenerator(user);
        }

        // mime post
        MultipartEntity entity = new DataSubmissionEntity(this, index);

        EncryptedFileBody fb = new EncryptedFileBody(f, getDecryptCipher(new SecretKeySpec(slr.getKey(), "AES")), "text/xml");
        entity.addPart("xml_submission_file", fb);

        HttpResponse response;
        try {
            response = generator.postData(submissionUrl, entity);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return false;
        }

        int responseCode = response.getStatusLine().getStatusCode();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try {
            AndroidStreamUtil.writeFromInputToOutput(response.getEntity().getContent(), bos);
        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
        }
        //TODO: Anything with the response?

        return (responseCode >= 200 && responseCode < 300);
    }

    private boolean removeLocalReports(SqlStorage<DeviceReportRecord> storage,
                                       ArrayList<Integer> submittedSuccesfullyIds,
                                       ArrayList<DeviceReportRecord> submittedSuccesfully) {
        try {
            //Wipe the DB entries
            storage.remove(submittedSuccesfullyIds);
        } catch (Exception e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Error deleting logs!" + e.getMessage());
            return false;
        }
        //Try to wipe the files, too, now that the file's submitted. (Not a huge deal if this fails, though)
        for (DeviceReportRecord record : submittedSuccesfully) {
            try {
                File f = new File(record.getFilePath());
                f.delete();
            } catch (Exception e) {
                //TODO: Anything useful here?
            }
        }
        return true;
    }

    private LogSubmitOutcomes checkSubmissionResult(int numberOfLogsToSubmit,
                                                    ArrayList<DeviceReportRecord> submittedSuccesfully) {
        if (submittedSuccesfully.size() > 0) {
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Succesfully submitted " + submittedSuccesfully.size() + " device reports to server.");
        }
        //Whether this is a full or partial success depends on how many logs were pending
        if (submittedSuccesfully.size() == numberOfLogsToSubmit) {
            return LogSubmitOutcomes.Submitted;
        } else {
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, numberOfLogsToSubmit - submittedSuccesfully.size() + " logs remain on phone.");
            //Some remain unsent
            return LogSubmitOutcomes.Serialized;
        }
    }

    private static Cipher getDecryptCipher(SecretKeySpec key) {
        Cipher cipher;
        try {
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher;
            //TODO: Something smart here;
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void beginSubmissionProcess(int totalItems) {
        this.publishProgress(LogSubmissionTask.SUBMISSION_BEGIN, (long)totalItems);
    }

    @Override
    public void startSubmission(int itemNumber, long length) {
        this.publishProgress(LogSubmissionTask.SUBMISSION_START, (long)itemNumber, length);
    }

    @Override
    public void notifyProgress(int itemNumber, long progress) {
        this.publishProgress(LogSubmissionTask.SUBMISSION_NOTIFY, (long)itemNumber, progress);
    }

    @Override
    public void endSubmissionProcess() {
        this.publishProgress(LogSubmissionTask.SUBMISSION_DONE);
    }

    @Override
    protected void onProgressUpdate(Long... values) {
        super.onProgressUpdate(values);

        if (values[0] == LogSubmissionTask.SUBMISSION_BEGIN) {
            listener.beginSubmissionProcess(values[1].intValue());
        } else if (values[0] == LogSubmissionTask.SUBMISSION_START) {
            listener.startSubmission(values[1].intValue(), values[2]);
        } else if (values[0] == LogSubmissionTask.SUBMISSION_NOTIFY) {
            listener.notifyProgress(values[1].intValue(), values[2]);
        } else if (values[0] == LogSubmissionTask.SUBMISSION_DONE) {
            listener.endSubmissionProcess();
        }
    }

    @Override
    protected void onPostExecute(LogSubmitOutcomes result) {
        super.onPostExecute(result);
        listener.endSubmissionProcess();
        if (result != LogSubmitOutcomes.Submitted) {
            CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(result));
        } else {
            CommCareApplication._().clearNotifications(result.getCategory());
        }
    }
}
