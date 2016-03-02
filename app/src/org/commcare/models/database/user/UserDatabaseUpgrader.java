package org.commcare.models.database.user;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.cases.ledger.Ledger;
import org.commcare.logging.AndroidLogEntry;
import org.commcare.logging.XPathErrorEntry;
import org.commcare.models.database.AndroidTableBuilder;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.SqlStorageIterator;
import org.commcare.models.database.app.DatabaseAppOpenHelper;
import org.commcare.models.database.global.models.ApplicationRecord;
import org.commcare.models.database.migration.FixtureSerializationMigration;
import org.commcare.models.database.user.models.ACase;
import org.commcare.models.database.user.models.ACasePreV6Model;
import org.commcare.models.database.user.models.AUser;
import org.commcare.models.database.user.models.CaseIndexTable;
import org.commcare.models.database.user.models.EntityStorageCache;
import org.commcare.models.database.user.models.FormRecord;
import org.commcare.models.database.user.models.FormRecordV1;
import org.commcare.models.database.user.models.GeocodeCacheModel;
import org.commcare.models.database.user.models.SessionStateDescriptor;
import org.javarosa.core.model.User;
import org.javarosa.core.services.storage.Persistable;

import java.util.Vector;

/**
 * @author ctsims
 */
class UserDatabaseUpgrader {
    private static final String TAG = UserDatabaseUpgrader.class.getSimpleName();

    private boolean inSenseMode = false;
    private final Context c;
    private final byte[] fileMigrationKey;
    private final String userKeyRecordId;

    public UserDatabaseUpgrader(Context c, String userKeyRecordId, boolean inSenseMode, byte[] fileMigrationKey) {
        this.c = c;
        this.userKeyRecordId = userKeyRecordId;
        this.inSenseMode = inSenseMode;
        this.fileMigrationKey = fileMigrationKey;
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            if (upgradeOneTwo(db)) {
                oldVersion = 2;
            }
        }

        if (oldVersion == 2) {
            if (upgradeTwoThree(db)) {
                oldVersion = 3;
            }
        }

        if (oldVersion == 3) {
            if (upgradeThreeFour(db)) {
                oldVersion = 4;
            }
        }

        if (oldVersion == 4) {
            if (upgradeFourFive(db)) {
                oldVersion = 5;
            }
        }

        if (oldVersion == 5) {
            if (upgradeFiveSix(db)) {
                oldVersion = 6;
            }
        }

        if (oldVersion == 6) {
            if (upgradeSixSeven(db)) {
                oldVersion = 7;
            }
        }

        if (oldVersion == 7) {
            if (upgradeSevenEight(db)) {
                oldVersion = 8;
            }
        }

        if (oldVersion == 8) {
            if (upgradeEightNine(db)) {
                oldVersion = 9;
            }
        }

        if (oldVersion == 9) {
            if (upgradeNineTen(db)) {
                oldVersion = 10;
            }
        }

        if (oldVersion == 10) {
            if (upgradeTenEleven(db)) {
                oldVersion = 11;
            }
        }

        if (oldVersion == 11) {
            if (upgradeElevenTwelve(db)) {
                oldVersion = 12;
            }
        }

        if (oldVersion == 12) {
            if (upgradeTwelveThirteen(db)) {
                oldVersion = 13;
            }
        }
    }

    private boolean upgradeOneTwo(final SQLiteDatabase db) {
        db.beginTransaction();
        try {
            markSenseIncompleteUnsent(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeTwoThree(final SQLiteDatabase db) {
        db.beginTransaction();
        try {
            markSenseIncompleteUnsent(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeThreeFour(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            addStockTable(db);
            updateIndexes(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFourFive(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("ledger_entity_id", "ledger", "entity_id"));
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFiveSix(SQLiteDatabase db) {
        //On some devices this process takes a significant amount of time (sorry!) we should
        //tell the service to wait longer to make sure this can finish.
        CommCareApplication._().setCustomServiceBindTimeout(60 * 5 * 1000);

        db.beginTransaction();
        try {
            db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_status_open_index", "AndroidCase", "case_type,case_status"));

            DbUtil.createNumbersTable(db);
            db.execSQL(EntityStorageCache.getTableDefinition());
            EntityStorageCache.createIndexes(db);

            db.execSQL(CaseIndexTable.getTableDefinition());
            CaseIndexTable.createIndexes(db);
            CaseIndexTable cit = new CaseIndexTable(db);

            //NOTE: Need to use the PreV6 case model any time we manipulate cases in this model for upgraders
            //below 6
            SqlStorage<ACase> caseStorage = new SqlStorage<ACase>(ACase.STORAGE_KEY, ACasePreV6Model.class, new ConcreteAndroidDbHelper(c, db));

            for (ACase c : caseStorage) {
                cit.indexCase(c);
            }


            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeSixSeven(SQLiteDatabase db) {
        //On some devices this process takes a significant amount of time (sorry!) we should
        //tell the service to wait longer to make sure this can finish.
        CommCareApplication._().setCustomServiceBindTimeout(60 * 5 * 1000);

        long start = System.currentTimeMillis();
        db.beginTransaction();
        try {
            SqlStorage<ACase> caseStorage = new SqlStorage<ACase>(ACase.STORAGE_KEY, ACasePreV6Model.class, new ConcreteAndroidDbHelper(c, db));
            updateModels(caseStorage);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
            Log.d(TAG, "Case model update complete in " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    /**
     * Depcrecate the old AUser object so that both platforms are using the User object
     * to represents users
     */
    private boolean upgradeSevenEight(SQLiteDatabase db) {
        //On some devices this process takes a significant amount of time (sorry!) we should
        //tell the service to wait longer to make sure this can finish.
        CommCareApplication._().setCustomServiceBindTimeout(60 * 5 * 1000);
        long start = System.currentTimeMillis();
        db.beginTransaction();
        try {
            SqlStorage<Persistable> userStorage = new SqlStorage<Persistable>(AUser.STORAGE_KEY, AUser.class, new ConcreteAndroidDbHelper(c, db));
            SqlStorageIterator<Persistable> iterator = userStorage.iterate();
            while (iterator.hasMore()) {
                AUser oldUser = (AUser)iterator.next();
                User newUser = oldUser.toNewUser();
                userStorage.write(newUser);
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
            Log.d(TAG, "Case model update complete in " + (System.currentTimeMillis() - start) + "ms");
        }
    }

    /*
     * Deserialize user fixtures in db using old form instance serialization
     * scheme, and re-serialize them using the new scheme that preserves
     * attributes.
     */
    private boolean upgradeEightNine(SQLiteDatabase db) {
        Log.d(TAG, "starting user fixture migration");

        FixtureSerializationMigration.stageFixtureTables(db);

        boolean didFixturesMigrate =
                FixtureSerializationMigration.migrateFixtureDbBytes(db, c, userKeyRecordId, fileMigrationKey);

        FixtureSerializationMigration.dropTempFixtureTable(db);
        return didFixturesMigrate;
    }

    /**
     * Adding an appId field to FormRecords, for compatibility with multiple apps functionality
     */
    private boolean upgradeNineTen(SQLiteDatabase db) {
        // This process could take a while, so tell the service to wait longer to make sure
        // it can finish
        CommCareApplication._().setCustomServiceBindTimeout(60 * 5 * 1000);

        db.beginTransaction();
        try {

            if (multipleInstalledAppRecords()) {
                // Cannot migrate FormRecords once this device has already started installing
                // multiple applications, because there is no way to know which of those apps the
                // existing FormRecords belong to
                deleteExistingFormRecordsAndWarnUser(c, db);
                addAppIdColumnToTable(db);
                db.setTransactionSuccessful();
                return true;
            }

            SqlStorage<FormRecordV1> oldStorage = new SqlStorage<>(
                    FormRecord.STORAGE_KEY,
                    FormRecordV1.class,
                    new ConcreteAndroidDbHelper(c, db));

            String appId = getInstalledAppRecord().getApplicationId();
            Vector<FormRecord> upgradedRecords = new Vector<>();
            // Create all of the updated records, based upon the existing ones
            for (FormRecordV1 oldRecord : oldStorage) {
                FormRecord newRecord = new FormRecord(
                        oldRecord.getInstanceURIString(),
                        oldRecord.getStatus(),
                        oldRecord.getFormNamespace(),
                        oldRecord.getAesKey(),
                        oldRecord.getInstanceID(),
                        oldRecord.lastModified(),
                        appId);
                newRecord.setID(oldRecord.getID());
                upgradedRecords.add(newRecord);
            }

            addAppIdColumnToTable(db);

            // Write all of the new records to the updated table
            SqlStorage<FormRecord> newStorage = new SqlStorage<>(
                    FormRecord.STORAGE_KEY,
                    FormRecord.class,
                    new ConcreteAndroidDbHelper(c, db));
            for (FormRecord r : upgradedRecords) {
                newStorage.write(r);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private static void addAppIdColumnToTable(SQLiteDatabase db) {
        // Alter the FormRecord table to include an app id column
        db.execSQL(DbUtil.addColumnToTable(
                FormRecord.STORAGE_KEY,
                FormRecord.META_APP_ID,
                "TEXT"));
    }

    private boolean upgradeTenEleven(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            // add table for dedicated xpath error logging for reporting xpath
            // errors on specific cc app builds.
            AndroidTableBuilder builder = new AndroidTableBuilder(XPathErrorEntry.STORAGE_KEY);
            builder.addData(new XPathErrorEntry());
            db.execSQL(builder.getTableCreateString());
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeElevenTwelve(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL("DROP TABLE IF EXISTS " + GeocodeCacheModel.STORAGE_KEY);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return true;
    }

    private boolean upgradeTwelveThirteen(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            AndroidTableBuilder builder = new AndroidTableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            db.execSQL(builder.getTableCreateString());

            builder = new AndroidTableBuilder(ForceCloseLogEntry.STORAGE_KEY);
            builder.addData(new ForceCloseLogEntry());
            db.execSQL(builder.getTableCreateString());

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private void updateIndexes(SQLiteDatabase db) {
        db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_id_index", "AndroidCase", "case_id"));
        db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_type_index", "AndroidCase", "case_type"));
        db.execSQL(DatabaseAppOpenHelper.indexOnTableCommand("case_status_index", "AndroidCase", "case_status"));
    }

    private void addStockTable(SQLiteDatabase db) {
        AndroidTableBuilder builder = new AndroidTableBuilder(Ledger.STORAGE_KEY);
        builder.addData(new Ledger());
        builder.setUnique(Ledger.INDEX_ENTITY_ID);
        db.execSQL(builder.getTableCreateString());
    }

    private void markSenseIncompleteUnsent(final SQLiteDatabase db) {
        //Fix for Bug in 2.7.0/1, forms in sense mode weren't being properly marked as complete after entry.
        if (inSenseMode) {

            //Get form record storage
            SqlStorage<FormRecord> storage = new SqlStorage<>(FormRecord.STORAGE_KEY, FormRecord.class, new ConcreteAndroidDbHelper(c, db));

            //Iterate through all forms currently saved
            for (FormRecord record : storage) {
                //Update forms marked as incomplete with the appropriate status
                if (FormRecord.STATUS_INCOMPLETE.equals(record.getStatus())) {
                    //update to complete to process/send.
                    storage.write(record.updateInstanceAndStatus(record.getInstanceURI().toString(), FormRecord.STATUS_COMPLETE));
                }
            }
        }
    }

    /**
     * Reads and rewrites all of the records in a table, generally to adapt an old serialization format to a new
     * format
     */
    private <T extends Persistable> void updateModels(SqlStorage<T> storage) {
        for (T t : storage) {
            storage.write(t);
        }
    }

    private static boolean multipleInstalledAppRecords() {
        SqlStorage<ApplicationRecord> storage =
                CommCareApplication._().getGlobalStorage(ApplicationRecord.class);
        int count = 0;
        for (ApplicationRecord r : storage) {
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED && r.resourcesValidated()) {
                count++;
            }
        }
        return (count > 1);
    }

    public static ApplicationRecord getInstalledAppRecord() {
        SqlStorage<ApplicationRecord> storage =
                CommCareApplication._().getGlobalStorage(ApplicationRecord.class);
        for (Persistable p : storage) {
            ApplicationRecord r = (ApplicationRecord)p;
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED && r.resourcesValidated()) {
                return r;
            }
        }
        return null;
    }

    private static void deleteExistingFormRecordsAndWarnUser(Context c, SQLiteDatabase db) {
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
        CommCareApplication._().storeMessageForUserOnDispatch(warningTitle, warningMessage);
    }
}
