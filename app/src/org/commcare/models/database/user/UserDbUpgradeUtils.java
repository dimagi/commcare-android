package org.commcare.models.database.user;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.FormRecordV1;
import org.commcare.android.database.user.models.FormRecordV2;
import org.commcare.android.database.user.models.FormRecordV3;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.cases.ledger.Ledger;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.modern.database.DatabaseIndexingUtils;
import org.commcare.modern.database.TableBuilder;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.storage.Persistable;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/**
 * Created by amstone326 on 3/8/17.
 */
public class UserDbUpgradeUtils {

    protected static void addAppIdColumnToTable(SQLiteDatabase db) {
        // Alter the FormRecord table to include an app id column
        db.execSQL(DbUtil.addColumnToTable(
                FormRecord.STORAGE_KEY,
                FormRecord.META_APP_ID,
                "TEXT"));
    }

    protected static void addFormNumberColumnToTable(SQLiteDatabase db) {
        // Alter the FormRecord table to include an app id column
        db.execSQL(DbUtil.addColumnToTable(
                FormRecord.STORAGE_KEY,
                FormRecord.META_SUBMISSION_ORDERING_NUMBER,
                "TEXT"));
    }

    protected static boolean multipleInstalledAppRecords() {
        SqlStorage<ApplicationRecord> storage =
                CommCareApplication.instance().getGlobalStorage(ApplicationRecord.class);
        int count = 0;
        for (ApplicationRecord r : storage) {
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED && r.resourcesValidated()) {
                count++;
            }
        }
        return (count > 1);
    }

    protected static ApplicationRecord getInstalledAppRecord() {
        SqlStorage<ApplicationRecord> storage =
                CommCareApplication.instance().getGlobalStorage(ApplicationRecord.class);
        for (Persistable p : storage) {
            ApplicationRecord r = (ApplicationRecord)p;
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED && r.resourcesValidated()) {
                return r;
            }
        }
        return null;
    }

    protected static void deleteExistingFormRecordsAndWarnUser(Context c, SQLiteDatabase db) {
        SqlStorage<FormRecordV1> formRecordStorage = new SqlStorage<>(
                FormRecord.STORAGE_KEY,
                FormRecordV1.class,
                new ConcreteAndroidDbHelper(c, db));

        SqlStorage<SessionStateDescriptor> ssdStorage = new SqlStorage<>(
                SessionStateDescriptor.STORAGE_KEY,
                SessionStateDescriptor.class,
                new ConcreteAndroidDbHelper(c, db));

        formRecordStorage.removeAll();
        ssdStorage.removeAll();

        String warningTitle = "Minor data loss during upgrade";
        String warningMessage = "Due to the experimental state of" +
                " multiple application seating, we were not able to migrate all of your app data" +
                " during upgrade. Any saved, incomplete, and unsent forms on the device were deleted.";
        CommCareApplication.instance().storeMessageForUserOnDispatch(warningTitle, warningMessage);
    }

    protected static void updateIndexes(SQLiteDatabase db) {
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("case_id_index", "AndroidCase", "case_id"));
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("case_type_index", "AndroidCase", "case_type"));
        db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("case_status_index", "AndroidCase", "case_status"));
    }

    protected static void addStockTable(SQLiteDatabase db) {
        TableBuilder builder = new TableBuilder(Ledger.STORAGE_KEY);
        builder.addData(new Ledger());
        builder.setUnique(Ledger.INDEX_ENTITY_ID);
        db.execSQL(builder.getTableCreateString());
    }

    protected static Set<String> getAppIdsForRecords(SqlStorage<FormRecordV2> oldFormRecords) {
        Set<String> appIds = new HashSet<>();
        for (FormRecordV2 formRecord : oldFormRecords) {
            appIds.add(formRecord.getAppId());
        }
        return appIds;
    }

    protected static void sortRecordsByDate(Vector<Integer> ids,
                                         SqlStorage<FormRecordV2> storage) {
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
                                                         SqlStorage<FormRecordV2> storage) {
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
                Logger.log(LogTypes.TYPE_ERROR_ASSERTION,
                        "Invalid date in last modified value: " + dateAsString);
                idToDateIndex.put(id, (long)id);
                continue;
            }
            idToDateIndex.put(id, dateAsSeconds);
        }
        return idToDateIndex;
    }

    protected static void addRelationshipToAllCases(Context c, SQLiteDatabase db) {
        SqlStorage<ACase> caseStorage = new SqlStorage<>(ACase.STORAGE_KEY, ACase.class,
                new ConcreteAndroidDbHelper(c, db));

        db.execSQL(DbUtil.addColumnToTable(
                AndroidCaseIndexTable.TABLE_NAME,
                "relationship",
                "TEXT"));

        AndroidCaseIndexTable indexTable = new AndroidCaseIndexTable(db);
        indexTable.reIndexAllCases(caseStorage);
    }

    protected static void migrateFormRecords(Context c, SQLiteDatabase db) {
        SqlStorage<FormRecordV3> oldStorage = new SqlStorage<>(
                FormRecord.STORAGE_KEY,
                FormRecordV3.class,
                new ConcreteAndroidDbHelper(c, db));

        SqlStorage<FormRecord> newStorage = new SqlStorage<>(
                FormRecord.STORAGE_KEY,
                FormRecord.class,
                new ConcreteAndroidDbHelper(c, db));
        for (FormRecordV3 oldRecord : oldStorage) {
            FormRecord newRecord = new FormRecord(
                    oldRecord.getInstanceURIString(),
                    oldRecord.getStatus(),
                    oldRecord.getFormNamespace(),
                    oldRecord.getAesKey(),
                    oldRecord.getInstanceID(),
                    oldRecord.lastModified(),
                    oldRecord.getAppId());
            newRecord.setID(oldRecord.getID());
            newStorage.write(newRecord);
        }
    }
}
