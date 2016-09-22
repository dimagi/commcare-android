package org.commcare.utils;

import android.support.annotation.NonNull;

import org.commcare.CommCareApplication;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.javarosa.core.services.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;

/**
 * Simple utility/helper methods for common operations across
 * the application
 *
 * @author ctsims
 */
public class StorageUtils {

    @NonNull
    public static Vector<Integer> getUnsentOrUnprocessedFormsForCurrentApp(
            SqlStorage<FormRecord> storage) {

        String currentAppId =
                CommCareApplication._().getCurrentApp().getAppRecord().getApplicationId();

        Vector<Integer> ids = storage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_UNSENT, currentAppId});
        ids.addAll(storage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_COMPLETE, currentAppId}));

        return ids;
    }

    public static int getNumIncompleteForms() {
        SqlStorage<FormRecord> formsStorage =
                CommCareApplication._().getUserStorage(FormRecord.class);
        String currentAppId =
                CommCareApplication._().getCurrentApp().getAppRecord().getApplicationId();

        return formsStorage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new String[]{FormRecord.STATUS_INCOMPLETE, currentAppId}).size();
    }

    public static FormRecord[] getUnsentRecords(SqlStorage<FormRecord> storage) {
        // TODO: This could all be one big sql query instead of doing it in code

        Vector<Integer> ids;
        try {
            ids = getUnsentOrUnprocessedFormsForCurrentApp(storage);
        } catch (SessionUnavailableException e) {
            ids = new Vector<>();
        }

        if (ids.size() == 0) {
            return new FormRecord[0];
        }

        // Order ids so they're submitted to and processed by the server in
        // the correct order.
        sortRecordsByDate(ids, storage);

        // The records should now be in order and we can pass to the next phase
        FormRecord[] records = new FormRecord[ids.size()];
        for (int i = 0; i < ids.size(); ++i) {
            records[i] = storage.read(ids.elementAt(i));
        }
        return records;
    }

    private static void sortRecordsByDate(Vector<Integer> ids,
                                          SqlStorage<FormRecord> storage) {
        final HashMap<Integer, Long> idToDateIndex =
                getIdToDateMap(ids, storage);

        Collections.sort(ids, new Comparator<Integer>() {
            @Override
            public int compare(Integer lhs, Integer rhs) {
                Long lhd = idToDateIndex.get(lhs);
                Long rhd = idToDateIndex.get(rhs);
                if (lhd < rhd) {
                    return -1;
                }
                if (lhd > rhd) {
                    return 1;
                }
                return 0;
            }
        });
    }

    private static HashMap<Integer, Long> getIdToDateMap(Vector<Integer> ids,
                                                         SqlStorage<FormRecord> storage) {
        HashMap<Integer, Long> idToDateIndex = new HashMap<>();
        for (int id : ids) {
            // Last modified for a unsent and complete forms is the formEnd
            // date that was captured and locked when form entry, so it's a
            // safe cannonical ordering
            String dateAsString =
                    storage.getMetaDataFieldForRecord(id, FormRecord.META_LAST_MODIFIED);
            long dateAsSeconds;
            try {
                dateAsSeconds = Long.valueOf(dateAsString);
            } catch (NumberFormatException e) {
                // Go with the next best ordering for now
                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,
                        "Invalid date in last modified value: " + dateAsString);
                idToDateIndex.put(id, (long)id);
                continue;
            }
            idToDateIndex.put(id, dateAsSeconds);
        }
        return idToDateIndex;
    }
}
