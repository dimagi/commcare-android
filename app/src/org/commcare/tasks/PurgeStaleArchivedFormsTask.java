package org.commcare.tasks;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.utils.FormUploadUtil;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

/**
 * Remove saved forms that have passed their validity date from the device. If
 * the user launches the saved forms list activity while this is running they
 * should be blocked until it completes as a matter of not allowing loading
 * forms that might be in the process of being purged.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class PurgeStaleArchivedFormsTask
        extends SingletonTask<Void, Void, Void> {
    private static final String DAYS_TO_RETAIN_SAVED_FORMS_KEY =
            "cc-days-form-retain";

    public static final String KEY_HAS_PERFORMED_HOTFIX_CHECK =
            "cc-internal-226-hotfix-check-performed";

    public static final int PURGE_STALE_ARCHIVED_FORMS_TASK_ID = 1283;
    private static PurgeStaleArchivedFormsTask singletonRunningInstance = null;
    private static final Object lock = new Object();

    private PurgeStaleArchivedFormsTask() {
        TAG = PurgeStaleArchivedFormsTask.class.getSimpleName();
    }

    public static PurgeStaleArchivedFormsTask getRunningInstance() {
        synchronized (lock) {
            if (singletonRunningInstance != null &&
                    singletonRunningInstance.getStatus() == Status.RUNNING) {
                return singletonRunningInstance;
            }
            return null;
        }
    }


    public static void launchPurgeTask() {
        synchronized (lock) {
            if (singletonRunningInstance == null) {
                singletonRunningInstance = new PurgeStaleArchivedFormsTask();
                singletonRunningInstance.execute();
            }
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        CommCareApp app = CommCareApplication._().getCurrentApp();

        performUnsentAttachmentHotfix(app);

        performArchivedFormPurge(app);
        return null;
    }

    @Override
    public void clearTaskInstance() {
        synchronized (lock) {
            singletonRunningInstance = null;
        }
    }

    /**
     * Purge saved forms from device that have surpassed the validity date set
     * by the app
     *
     * @param ccApp Used to get the saved form validity date property.
     */
    private static void performArchivedFormPurge(CommCareApp ccApp) {
        int daysSavedFormIsValidFor = getArchivedFormsValidityInDays(ccApp);
        if (daysSavedFormIsValidFor == -1) {
            return;
        }

        DateTime lastValidDate =
                getLastValidArchivedFormDate(daysSavedFormIsValidFor);

        Vector<Integer> toPurge = getSavedFormsToPurge(lastValidDate);

        for (int formRecord : toPurge) {
            FormRecordCleanupTask.wipeRecord(CommCareApplication._(), formRecord);
        }
    }

    /**
     * Read the validity range for archived forms out of app preferences.
     *
     * @return how long archived forms should kept on the phone. -1 if saved
     * forms should be kept indefinitely
     */
    public static int getArchivedFormsValidityInDays(CommCareApp ccApp) {
        int daysForReview = -1;
        String daysToPurge =
                ccApp.getAppPreferences().getString(DAYS_TO_RETAIN_SAVED_FORMS_KEY, "-1");
        try {
            daysForReview = Integer.parseInt(daysToPurge);
        } catch (NumberFormatException nfe) {
            Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE,
                    "Invalid days to purge: " + daysToPurge);
        }
        return daysForReview;
    }

    private static DateTime getLastValidArchivedFormDate(int daysForReview) {
        return DateTime.now().minusDays(daysForReview);
    }

    /**
     * @return List of form record ids that correspond to forms that have
     * passed the saved form validity date range
     */
    public static Vector<Integer> getSavedFormsToPurge(DateTime lastValidDate) {
        Vector<Integer> toPurge = new Vector<>();

        SqlStorage<FormRecord> formStorage =
                CommCareApplication._().getUserStorage(FormRecord.class);

        String currentAppId = CommCareApplication._().getCurrentApp().getAppRecord().getApplicationId();
        Vector<Integer> savedFormsForThisApp = formStorage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_SAVED, currentAppId});

        for (int id : savedFormsForThisApp) {
            String date =
                    formStorage.getMetaDataFieldForRecord(id, FormRecord.META_LAST_MODIFIED);
            try {
                DateTime modifiedDate = parseModifiedDate(date);
                if (modifiedDate.isBefore(lastValidDate)) {
                    toPurge.add(id);
                }
            } catch (Exception e) {
                Logger.log(AndroidLogger.SOFT_ASSERT,
                        "Unable to parse modified date of form record: " + date);
                toPurge.add(id);
            }
        }
        return toPurge;
    }

    private static DateTime parseModifiedDate(String dateString)
            throws ParseException {
        // TODO PLM: the use of text timezones is buggy because timezone
        // abbreviations aren't unique. We need to do a refactor to use a
        // string date representation that is easily parsable by Joda's
        // DateTime.
        SimpleDateFormat format =
                new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        Date parsed = format.parse(dateString);
        return new DateTime(parsed);
    }

    /**
     * This is a temporary fix for a problem which occured with specific dates where forms with
     * attachmetns were not received properly. It can be removed on June 1, 2016
     *
     * The method identifies forms saved in a specific date window that need to be resubmitted
     * and performs the resubmission.
     */
    private static void performUnsentAttachmentHotfix(CommCareApp app) {
        if(app.getAppPreferences().getBoolean(KEY_HAS_PERFORMED_HOTFIX_CHECK, false)) {
            return;
        }

        DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();
        DateTime timeWindowBegin = parser.parseDateTime("2016-02-22T06:00:00-05:00");
        DateTime timeWindowEnd = parser.parseDateTime("2016-03-04T06:00:00-05:00");

        Vector<Integer> toResend = evaluateSavedFormsToResend(timeWindowBegin, timeWindowEnd);

        markFormsAsUnsent(toResend);

        if(toResend.size() > 0) {
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Succesfully recovered " + toResend.size() +
                    " forms for resubmission");
        }
        app.getAppPreferences().edit().putBoolean(KEY_HAS_PERFORMED_HOTFIX_CHECK, true).commit();
    }


    private static Vector<Integer> evaluateSavedFormsToResend(DateTime timeWindowBegin,
                                                              DateTime timeWindowEnd) {
        Vector<Integer> toResend = new Vector<>();

        SqlStorage<FormRecord> formStorage =
                CommCareApplication._().getUserStorage(FormRecord.class);

        String currentAppId = CommCareApplication._().getCurrentApp().getAppRecord().getApplicationId();
        Vector<Integer> savedFormsForThisApp = formStorage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_SAVED, currentAppId});

        for (int id : savedFormsForThisApp) {
            String date =
                    formStorage.getMetaDataFieldForRecord(id, FormRecord.META_LAST_MODIFIED);
            try {
                DateTime modifiedDate = parseModifiedDate(date);
                if (modifiedDate.isAfter(timeWindowBegin) && modifiedDate.isBefore(timeWindowEnd)) {
                    if(evaluateFormRecordForAttachments(id, formStorage)) {
                        toResend.add(id);
                    }
                }
            } catch (Exception e) {
                //Note: These can't pile up because we purge them by default
                Logger.log(AndroidLogger.SOFT_ASSERT,
                        "Unable to parse modified date of form record during recovery: " + date);
                toResend.add(id);
            }
        }
        return toResend;

    }

    private static boolean evaluateFormRecordForAttachments(int formRecordId,
                                                            SqlStorage<FormRecord> formStorage) {
        FormRecord record = formStorage.read(formRecordId);
        try {
            File formFolder = getFormRecordFolder(record);
            for(File file : formFolder.listFiles()) {
                if(!file.getName().endsWith(".xml") &&
                        FormUploadUtil.isSupportedMultimediaFile(file.getName())) {
                    return true;
                }
            }

        } catch(FileNotFoundException e ) {
            return false;
        }
        return false;
    }

    private static File getFormRecordFolder(FormRecord record) throws FileNotFoundException{
        String filePath = record.getPath(CommCareApplication._());
        try {
            return new File(filePath).getCanonicalFile().getParentFile();
        } catch(IOException e) {
            throw new FileNotFoundException(filePath + " not available to cannonicalize");
        }
    }

    private static void markFormsAsUnsent(Vector<Integer> toResend) {
        SqlStorage<FormRecord> formStorage =
                CommCareApplication._().getUserStorage(FormRecord.class);

        for(int formRecordId : toResend) {
            FormRecord r = formStorage.read(formRecordId);
            r.setArchivedFormToUnsent();
            formStorage.write(r);
        }
    }

}
