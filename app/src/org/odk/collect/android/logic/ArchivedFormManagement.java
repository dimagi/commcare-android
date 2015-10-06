package org.odk.collect.android.logic;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;

/**
 * Determines when saved forms should be purged.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class ArchivedFormManagement {

    /**
     * Purge saved forms from device that have surpassed the validity date set
     * by the app
     *
     * @param app Used to get the saved form validity date property.
     */
    public static void performArchivedFormPurge(CommCareApp app) {
        int daysSavedFormIsValidFor = getArchivedFormsValidityInDays(app);
        if (daysSavedFormIsValidFor == -1) {
            return;
        }

        DateTime lastValidDate = getLastValidArchivedFormDate(daysSavedFormIsValidFor);

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
    public static int getArchivedFormsValidityInDays(CommCareApp app) {
        int daysForReview = -1;
        String daysToPurge =
                app.getAppPreferences().getString("cc-days-form-retain", "-1");
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

    private static DateTime parseModifiedDate(String dateString) throws ParseException {
        // TODO PLM: the use of text timezones is buggy because timezone
        // abbreviations aren't unique. We need to do a refactor to use a
        // string date representation that is easily parsable by Joda's
        // DateTime.
        SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        Date parsed = format.parse(dateString);
        return new DateTime(parsed);
    }
}
