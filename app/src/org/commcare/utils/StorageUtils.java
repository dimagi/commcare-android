package org.commcare.utils;

import android.support.annotation.NonNull;

import org.commcare.CommCareApplication;
import org.commcare.models.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;

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
    public static Vector<Integer> getUnsentOrUnprocessedFormIdsForCurrentApp(
            SqlStorage<FormRecord> storage) {

        String currentAppId =
                CommCareApplication.instance().getCurrentApp().getAppRecord().getApplicationId();

        Vector<Integer> ids = storage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_UNSENT, currentAppId});
        ids.addAll(storage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_COMPLETE, currentAppId}));

        return ids;
    }

    public static Vector<FormRecord> getUnsentOrUnprocessedFormRecordsForCurrentApp(
            SqlStorage<FormRecord> storage) {

        String currentAppId =
                CommCareApplication.instance().getCurrentApp().getAppRecord().getApplicationId();

        Vector<FormRecord> records = storage.getRecordsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_UNSENT, currentAppId});
        records.addAll(storage.getRecordsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new Object[]{FormRecord.STATUS_COMPLETE, currentAppId}));

        return records;
    }

    public static int getNumIncompleteForms() {
        SqlStorage<FormRecord> formsStorage =
                CommCareApplication.instance().getUserStorage(FormRecord.class);
        String currentAppId =
                CommCareApplication.instance().getCurrentApp().getAppRecord().getApplicationId();

        return formsStorage.getIDsForValues(
                new String[]{FormRecord.META_STATUS, FormRecord.META_APP_ID},
                new String[]{FormRecord.STATUS_INCOMPLETE, currentAppId}).size();
    }

    public static FormRecord[] getUnsentRecordsForCurrentApp(SqlStorage<FormRecord> storage,
                                                             boolean sortRequired) {
        // TODO: This could all be one big sql query instead of doing it in code
        Vector<FormRecord> records;
        try {
            records = getUnsentOrUnprocessedFormRecordsForCurrentApp(storage);
        } catch (SessionUnavailableException e) {
            records = new Vector<>();
        }

        if (records.size() == 0) {
            return new FormRecord[0];
        }

        if (sortRequired) {
            // Order ids so they're submitted to and processed by the server in the correct order.
            sortRecordsBySubmissionOrderingNumber(records);
        }

        FormRecord[] recordArray = new FormRecord[records.size()];
        for (int i = 0; i < records.size(); ++i) {
            recordArray[i] = records.get(i);
        }
        return recordArray;
    }

    private static void sortRecordsBySubmissionOrderingNumber(Vector<FormRecord> records) {
        Collections.sort(records, new Comparator<FormRecord>() {
            @Override
            public int compare(FormRecord form1, FormRecord form2) {
                int form1OrderingNum = form1.getSubmissionOrderingNumber();
                int form2OrderingNum = form2.getSubmissionOrderingNumber();
                if (form1OrderingNum < form2OrderingNum) {
                    return -1;
                }
                if (form1OrderingNum > form2OrderingNum) {
                    return 1;
                }
                return 0;
            }
        });
    }

}
