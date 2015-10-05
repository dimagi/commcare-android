package org.odk.collect.android.logic;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;

import java.util.Date;
import java.util.Vector;

/**
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
        long lastValidDate = getLastValidArchivedFormDate(app);

        if (lastValidDate == -1) {
            return;
        }

        Vector<Integer> toPurge = getSavedFormsToPurge(lastValidDate);

        if (toPurge.size() > 0) {
            Logger.log(AndroidLogger.TYPE_MAINTENANCE, "Purging " + toPurge.size() + " archived forms for being before the last valid date " + new Date(lastValidDate).toString());
            //Actually purge the old forms
            for (int formRecord : toPurge) {
                FormRecordCleanupTask.wipeRecord(CommCareApplication._(), formRecord);
            }
        }
    }

    public static long getLastValidArchivedFormDate(CommCareApp app) {
        int daysForReview = -1;
        String daysToPurge = app.getAppPreferences().getString("cc-days-form-retain", "-1");
        try {
            daysForReview = Integer.parseInt(daysToPurge);
        } catch (NumberFormatException nfe) {
            Logger.log(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, "Invalid days to purge: " + daysToPurge);
        }

        //If we don't define a days for review flag, we should just keep the forms around
        //indefinitely
        if (daysForReview == -1) {
            return -1;
        }
        return (new Date().getTime()) - (daysForReview * 24 * 60 * 60 * 1000);
    }

    public static Vector<Integer> getSavedFormsToPurge(long lastValidDate) {
        Vector<Integer> toPurge = new Vector<>();
        SqlStorage<FormRecord> forms = CommCareApplication._().getUserStorage(FormRecord.class);
        //Get all saved forms currently in storage
        for (int id : forms.getIDsForValue(FormRecord.META_STATUS, FormRecord.STATUS_SAVED)) {
            String date = forms.getMetaDataFieldForRecord(id, FormRecord.META_LAST_MODIFIED);

            try {
                //If the date the form was saved is before the last valid date, we can purge it
                long formModifiedDate = Date.parse(date);
                if (formModifiedDate < lastValidDate) {
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
