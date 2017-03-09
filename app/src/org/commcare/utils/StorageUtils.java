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
    public static Vector<Integer> getUnsentOrUnprocessedFormsForCurrentApp(
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

    public static int getNumIncompleteForms() {
        SqlStorage<FormRecord> formsStorage =
                CommCareApplication.instance().getUserStorage(FormRecord.class);
        String currentAppId =
                CommCareApplication.instance().getCurrentApp().getAppRecord().getApplicationId();

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

        // Order ids so they're submitted to and processed by the server in the correct order.
        sortRecordsByGlobalCounter(ids, storage);

        // The records should now be in order and we can pass to the next phase
        FormRecord[] records = new FormRecord[ids.size()];
        for (int i = 0; i < ids.size(); ++i) {
            records[i] = storage.read(ids.elementAt(i));
        }
        return records;
    }

    private static void sortRecordsByGlobalCounter(Vector<Integer> ids,
                                                   SqlStorage<FormRecord> storage) {
        final HashMap<Integer, Integer> idToFormNumberMapping = getIdToFormNumberMap(ids, storage);

        Collections.sort(ids, new Comparator<Integer>() {
            @Override
            public int compare(Integer formId1, Integer formId2) {
                Integer formNum1 = idToFormNumberMapping.get(formId1);
                Integer formNum2 = idToFormNumberMapping.get(formId2);
                if (formNum1 < formNum2) {
                    return -1;
                }
                if (formNum1 > formNum2) {
                    return 1;
                }
                return 0;
            }
        });
    }

    private static HashMap<Integer, Integer> getIdToFormNumberMap(Vector<Integer> ids,
                                                               SqlStorage<FormRecord> storage) {
        HashMap<Integer, Integer> idToFormNumberMapping = new HashMap<>();
        for (int id : ids) {
            String formNumberAsString =
                    storage.getMetaDataFieldForRecord(id, FormRecord.META_FORM_ORDERING_NUMBER);
            idToFormNumberMapping.put(id, Integer.parseInt(formNumberAsString));
        }
        return idToFormNumberMapping;
    }
}
