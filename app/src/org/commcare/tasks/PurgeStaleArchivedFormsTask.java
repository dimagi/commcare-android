package org.commcare.tasks;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.models.database.SqlStorage;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;

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
                singletonRunningInstance.executeParallel();
            }
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        CommCareApp app = CommCareApplication.instance().getCurrentApp();

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

        for (int recordId : toPurge) {
            FormRecordCleanupTask.wipeRecord(recordId);
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
            Logger.log(LogTypes.TYPE_ERROR_CONFIG_STRUCTURE,
                    "Invalid value obtained for days to purge: " + daysToPurge);
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
                CommCareApplication.instance().getUserStorage(FormRecord.class);

        String currentAppId = CommCareApplication.instance().getCurrentApp().getAppRecord().getApplicationId();
        Vector<Integer> savedFormsForThisApp = formStorage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_SAVED, currentAppId});

        for (int id : savedFormsForThisApp) {
            String dateAsString =
                    formStorage.getMetaDataFieldForRecord(id, FormRecord.META_LAST_MODIFIED);
            long timeSinceEpoch;
            try {
                timeSinceEpoch = Long.valueOf(dateAsString);
            } catch (NumberFormatException e) {
                Logger.log(LogTypes.SOFT_ASSERT,
                        "Unable to parse modified date of form record: " + dateAsString);
                toPurge.add(id);
                continue;
            }

            DateTime modifiedDate = new DateTime(timeSinceEpoch);
            if (modifiedDate.isBefore(lastValidDate)) {
                toPurge.add(id);
            }
        }
        return toPurge;
    }
}
