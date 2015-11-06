package org.commcare.android.tasks;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;

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
        for (int id : formStorage.getIDsForValue(FormRecord.META_STATUS, FormRecord.STATUS_SAVED)) {
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
}
