package org.commcare.tasks;

import android.content.SharedPreferences;
import android.os.AsyncTask;

import org.commcare.CommCareApplication;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.android.logging.ForceCloseLogSerializer;
import org.commcare.logging.AndroidLogSerializer;
import org.commcare.logging.DeviceReportWriter;
import org.commcare.logging.XPathErrorEntry;
import org.commcare.logging.XPathErrorSerializer;
import org.commcare.models.database.SqlStorage;
import org.commcare.network.CommcareRequestGenerator;
import org.commcare.preferences.ServerUrls;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.tasks.LogSubmissionTask.LogSubmitOutcomes;
import org.commcare.util.LogTypes;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.model.User;
import org.javarosa.core.services.Logger;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
/**
 * @author ctsims
 */
public class LogSubmissionTask extends AsyncTask<Void, Long, LogSubmitOutcomes> implements DataSubmissionListener {

    //Stole from the process and send task. See if we can unify a lot of this behavior
    private static final long SUBMISSION_BEGIN = 16;
    private static final long SUBMISSION_START = 32;
    private static final long SUBMISSION_NOTIFY = 64;
    private static final long SUBMISSION_DONE = 128;

    protected enum LogSubmitOutcomes implements MessageTag {
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

        @Override
        public String getLocaleKeyBase() {
            return root;
        }

        @Override
        public String getCategory() {
            return "log_submission";
        }
    }

    private boolean serializeCurrentLogs = false;
    private final DataSubmissionListener listener;
    private final String submissionUrl;

    public LogSubmissionTask(boolean serializeCurrentLogs,
                             DataSubmissionListener listener,
                             String submissionUrl) {
        this.serializeCurrentLogs = serializeCurrentLogs;
        this.listener = listener;
        this.submissionUrl = submissionUrl;
    }

    public static String getSubmissionUrl(SharedPreferences appPreferences) {
        return appPreferences.getString(ServerUrls.PREFS_LOG_POST_URL_KEY,
                appPreferences.getString(ServerUrls.PREFS_SUBMISSION_URL_KEY, null));
    }

    @Override
    protected LogSubmitOutcomes doInBackground(Void... params) {
        try {
            SqlStorage<DeviceReportRecord> storage =
                    CommCareApplication.instance().getUserStorage(DeviceReportRecord.class);

            if (serializeCurrentLogs && !serializeLogs(storage)) {
                return LogSubmitOutcomes.Error;
            }

            // See how many we have pending to submit
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
        } catch (SessionUnavailableException e) {
            // The user database closed on us
            return LogSubmitOutcomes.Error;
        }
    }

    /**
     * Serialize all of the entries currently in Android logs, Xpath error logs, and Force close
     * logs, and write that to a DeviceReportRecord, which then gets added to the internal storage
     * object of all DeviceReportRecords that have yet to be submitted
     */
    private static boolean serializeLogs(SqlStorage<DeviceReportRecord> storage) {
        SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();

        //update the last recorded record
        settings.edit().putLong(HiddenPreferences.LOG_LAST_DAILY_SUBMIT, new Date().getTime()).commit();

        DeviceReportRecord record = DeviceReportRecord.generateNewRecordStub();

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

            // Serialize regular and xpath error logs for the current user
            AndroidLogSerializer<AndroidLogEntry> userLogSerializer = new AndroidLogSerializer<>(
                    CommCareApplication.instance().getUserStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class));
            reporter.addReportElement(userLogSerializer);

            XPathErrorSerializer xpathErrorSerializer = new XPathErrorSerializer(
                    CommCareApplication.instance().getUserStorage(XPathErrorEntry.STORAGE_KEY, XPathErrorEntry.class));
            reporter.addReportElement(xpathErrorSerializer);

            // Serialize all force close logs -- these can exist in both user and global storage
            ForceCloseLogSerializer globalForceCloseSerializer = new ForceCloseLogSerializer(
                    CommCareApplication.instance().getGlobalStorage(ForceCloseLogEntry.STORAGE_KEY, ForceCloseLogEntry.class));
            reporter.addReportElement(globalForceCloseSerializer);
            ForceCloseLogSerializer userForceCloseSerializer = new ForceCloseLogSerializer(
                    CommCareApplication.instance().getUserStorage(ForceCloseLogEntry.STORAGE_KEY, ForceCloseLogEntry.class));
            reporter.addReportElement(userForceCloseSerializer);

            // TEMPORARILY ONLY - serialize all force close logs in the old format, so that HQ
            // still picks them up, until we start processing the new format
            AndroidLogSerializer<ForceCloseLogEntry> globalForceCloseSerializer_oldFormat = new AndroidLogSerializer<>(
                    CommCareApplication.instance().getGlobalStorage(ForceCloseLogEntry.STORAGE_KEY, ForceCloseLogEntry.class));
            reporter.addReportElement(globalForceCloseSerializer_oldFormat);
            AndroidLogSerializer<ForceCloseLogEntry> userForceCloseSerializer_oldFormat = new AndroidLogSerializer<>(
                    CommCareApplication.instance().getUserStorage(ForceCloseLogEntry.STORAGE_KEY, ForceCloseLogEntry.class));
            reporter.addReportElement(userForceCloseSerializer_oldFormat);

            // Serialize all logs currently in global storage, since we have no way to determine
            // which app they truly belong to
            AndroidLogSerializer globalLogSerializer = new AndroidLogSerializer(
                    CommCareApplication.instance().getGlobalStorage(AndroidLogEntry.STORAGE_KEY, AndroidLogEntry.class));
            reporter.addReportElement(globalLogSerializer);

            // Write serialized logs to the record
            reporter.write();

            // Write this DeviceReportRecord to where all logs are saved to
            storage.write(record);

            // The logs are saved and recorded, so we can feel safe clearing the logs we serialized.
            userLogSerializer.purge();
            globalLogSerializer.purge();
            xpathErrorSerializer.purge();
            globalForceCloseSerializer.purge();
            userForceCloseSerializer.purge();
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
                if (submitDeviceReportRecord(slr, submissionUrl, this, index)) {
                    submittedSuccesfullyIds.add(slr.getID());
                    submittedSuccesfully.add(slr);
                }
                index++;
            } catch (Exception e) {

            }
        }
    }

    private static boolean submitDeviceReportRecord(DeviceReportRecord slr, String submissionUrl,
                                                    DataSubmissionListener listener, int index) {
        //Get our file pointer
        File f = new File(slr.getFilePath());

        // Bad (Empty) record. Wipe
        if (f.length() == 0) {
            return true;
        }

        listener.startSubmission(index, f.length());

        CommcareRequestGenerator generator;
        User user;
        try {
            user = CommCareApplication.instance().getSession().getLoggedInUser();
        } catch (SessionUnavailableException e) {
            // lost the session, so report failed submission
            return false;
        }

        generator = new CommcareRequestGenerator(user);

        List<MultipartBody.Part> parts = new ArrayList<>();

        parts.add(FormUploadUtil.createEncryptedFilePart(
                "xml_submission_file",
                f,
                "text/xml",
                new SecretKeySpec(slr.getKey(), "AES")));

        Response<ResponseBody> response;
        try {
            response = generator.postMultipart(submissionUrl, parts);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return false;
        }

        int responseCode = response.code();
        return (responseCode >= 200 && responseCode < 300);
    }

    private static boolean removeLocalReports(SqlStorage<DeviceReportRecord> storage,
                                              ArrayList<Integer> submittedSuccesfullyIds,
                                              ArrayList<DeviceReportRecord> submittedSuccesfully) {
        try {
            //Wipe the DB entries
            storage.remove(submittedSuccesfullyIds);
        } catch (Exception e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Error deleting logs!" + e.getMessage());
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
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Succesfully submitted " + submittedSuccesfully.size() + " device reports to server.");
        }
        //Whether this is a full or partial success depends on how many logs were pending
        if (submittedSuccesfully.size() == numberOfLogsToSubmit) {
            return LogSubmitOutcomes.Submitted;
        } else {
            Logger.log(LogTypes.TYPE_MAINTENANCE, numberOfLogsToSubmit - submittedSuccesfully.size() + " logs remain on phone.");
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
    public void startSubmission(int itemNumber, long sizeOfItem) {
        this.publishProgress(LogSubmissionTask.SUBMISSION_START, (long)itemNumber, sizeOfItem);
    }

    @Override
    public void notifyProgress(int itemNumber, long progress) {
        this.publishProgress(LogSubmissionTask.SUBMISSION_NOTIFY, (long)itemNumber, progress);
    }

    @Override
    public void endSubmissionProcess(boolean success) {
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
            listener.endSubmissionProcess(true);
        }
    }

    @Override
    protected void onPostExecute(LogSubmitOutcomes result) {
        super.onPostExecute(result);
        listener.endSubmissionProcess(LogSubmitOutcomes.Submitted.equals(result));
        if (result != LogSubmitOutcomes.Submitted) {
            CommCareApplication.notificationManager().reportNotificationMessage(NotificationMessageFactory.message(result));
        } else {
            CommCareApplication.notificationManager().clearNotifications(result.getCategory());
        }
    }
}
