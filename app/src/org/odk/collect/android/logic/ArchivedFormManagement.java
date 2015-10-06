package org.odk.collect.android.logic;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.joda.time.DateTime;

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
     * Check through user storage and identify whether there are any forms
     * which can be purged from the device.
     *
     * @param app The current app
     */
    public static void performArchivedFormPurge(CommCareApp app) {
        //Get the last date for froms to be valid (n days prior to today)
        int daysSavedFormIsValidFor = getArchivedFormsValidityInDays(app);
        if (daysSavedFormIsValidFor == -1) {
            return;
        }

        DateTime lastValidDate = getLastValidArchivedFormDate(daysSavedFormIsValidFor, DateTime.now());

        Vector<Integer> toPurge = getSavedFormsToPurge(lastValidDate);

        if (toPurge.size() > 0) {
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Purging " + toPurge.size() + " archived forms for being before the last valid date " + lastValidDate);
            //Actually purge the old forms
            for (int formRecord : toPurge) {
                FormRecordCleanupTask.wipeRecord(CommCareApplication._(), formRecord);
            }
        }
    }

    public static int getArchivedFormsValidityInDays(CommCareApp app) {
        int daysForReview = -1;
        String daysToPurge = app.getAppPreferences().getString("cc-days-form-retain", "-1");
        try {
            daysForReview = Integer.parseInt(daysToPurge);
        } catch (NumberFormatException nfe) {
            Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Invalid days to purge: " + daysToPurge);
        }
        return daysForReview;
    }

    public static DateTime getLastValidArchivedFormDate(int daysForReview, DateTime currentDate) {
        return currentDate.plusDays(daysForReview);
    }

    public static Vector<Integer> getSavedFormsToPurge(DateTime lastValidDate) {
        Vector<Integer> toPurge = new Vector<>();
        SqlStorage<FormRecord> forms = CommCareApplication._().getUserStorage(FormRecord.class);
        //Get all saved forms currently in storage
        for (int id : forms.getIDsForValue(FormRecord.META_STATUS, FormRecord.STATUS_SAVED)) {
            String date = forms.getMetaDataFieldForRecord(id, FormRecord.META_LAST_MODIFIED);

            try {
                //If the date the form was saved is before the last valid date, we can purge it
                SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
                Date parsed = format.parse(date);
                DateTime dt = new DateTime(parsed);
                if (dt.isBefore(lastValidDate)) {
                    toPurge.add(id);
                }
            } catch (Exception e) {
                //Catch all for now, we know that at least "" and null
                //are causing problems (neither of which should be acceptable
                //but if we see them, we should consider the form
                //purgable.
                toPurge.add(id);
            }
        }
        return toPurge;
    }
}
