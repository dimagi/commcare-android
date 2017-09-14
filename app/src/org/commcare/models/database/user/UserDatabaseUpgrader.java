package org.commcare.models.database.user;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecordV2;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.cases.model.StorageIndexedTreeElementModel;
import org.commcare.logging.XPathErrorEntry;
import org.commcare.modern.database.TableBuilder;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.IndexedFixturePathUtils;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.SqlStorageIterator;
import org.commcare.models.database.migration.FixtureSerializationMigration;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.ACasePreV6Model;
import org.commcare.android.database.user.models.AUser;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.models.database.user.models.EntityStorageCache;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.FormRecordV1;
import org.commcare.android.database.user.models.GeocodeCacheModel;
import org.commcare.modern.database.DatabaseIndexingUtils;
import org.javarosa.core.model.User;
import org.javarosa.core.services.storage.Persistable;

import java.util.Set;
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
        if (oldVersion == 13) {
            if (upgradeThirteenFourteen(db)) {
                oldVersion = 14;
            }
        }

        if (oldVersion == 14) {
            if (upgradeFourteenFifteen(db)) {
                oldVersion = 15;
            }
        }
        if (oldVersion == 15) {
            if (upgradeFifteenSixteen(db)) {
                oldVersion = 16;
            }
        }
        if (oldVersion == 16) {
            if (upgradeSixteenSeventeen(db)) {
                oldVersion = 17;
            }
        }
        if (oldVersion == 17) {
            if (upgradeSeventeenEighteen(db)) {
                oldVersion = 18;
            }
        }
        if (oldVersion == 18) {
            if (upgradeEighteenNineteen(db)) {
                oldVersion = 19;
            }
        }
        if (oldVersion == 19) {
            if (upgradeNineteenTwenty(db)) {
                oldVersion = 20;
            }
        }
        if (oldVersion == 20) {
            if (upgradeTwentyTwentyOne(db)) {
                oldVersion = 21;
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
            UserDbUpgradeUtils.addStockTable(db);
            UserDbUpgradeUtils.updateIndexes(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFourFive(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("ledger_entity_id", "ledger", "entity_id"));
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFiveSix(SQLiteDatabase db) {
        //On some devices this process takes a significant amount of time (sorry!) we should
        //tell the service to wait longer to make sure this can finish.
        CommCareApplication.instance().setCustomServiceBindTimeout(60 * 5 * 1000);

        db.beginTransaction();
        try {
            db.execSQL(DatabaseIndexingUtils.indexOnTableCommand("case_status_open_index", "AndroidCase", "case_type,case_status"));

            DbUtil.createNumbersTable(db);
            db.execSQL(EntityStorageCache.getTableDefinition());
            EntityStorageCache.createIndexes(db);

            db.execSQL(AndroidCaseIndexTable.getTableDefinition());
            AndroidCaseIndexTable.createIndexes(db);
            AndroidCaseIndexTable cit = new AndroidCaseIndexTable(db);

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
        CommCareApplication.instance().setCustomServiceBindTimeout(60 * 5 * 1000);

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
        CommCareApplication.instance().setCustomServiceBindTimeout(60 * 5 * 1000);
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
        CommCareApplication.instance().setCustomServiceBindTimeout(60 * 5 * 1000);

        db.beginTransaction();
        try {

            if (UserDbUpgradeUtils.multipleInstalledAppRecords()) {
                // Cannot migrate FormRecords once this device has already started installing
                // multiple applications, because there is no way to know which of those apps the
                // existing FormRecords belong to
                UserDbUpgradeUtils.deleteExistingFormRecordsAndWarnUser(c, db);
                UserDbUpgradeUtils.addAppIdColumnToTable(db);
                db.setTransactionSuccessful();
                return true;
            }

            SqlStorage<FormRecordV1> oldStorage = new SqlStorage<>(
                    FormRecord.STORAGE_KEY,
                    FormRecordV1.class,
                    new ConcreteAndroidDbHelper(c, db));

            String appId = UserDbUpgradeUtils.getInstalledAppRecord().getApplicationId();
            Vector<FormRecordV2> upgradedRecords = new Vector<>();
            // Create all of the updated records, based upon the existing ones
            for (FormRecordV1 oldRecord : oldStorage) {
                FormRecordV2 newRecord = new FormRecordV2(
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

            UserDbUpgradeUtils.addAppIdColumnToTable(db);

            // Write all of the new records to the updated table
            SqlStorage<FormRecordV2> newStorage = new SqlStorage<>(
                    FormRecord.STORAGE_KEY,
                    FormRecordV2.class,
                    new ConcreteAndroidDbHelper(c, db));
            for (FormRecordV2 r : upgradedRecords) {
                newStorage.write(r);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeTenEleven(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            // add table for dedicated xpath error logging for reporting xpath
            // errors on specific cc app builds.
            TableBuilder builder = new TableBuilder(XPathErrorEntry.STORAGE_KEY);
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
            TableBuilder builder = new TableBuilder(AndroidLogEntry.STORAGE_KEY);
            builder.addData(new AndroidLogEntry());
            db.execSQL(builder.getTableCreateString());

            builder = new TableBuilder(ForceCloseLogEntry.STORAGE_KEY);
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

    private boolean upgradeThirteenFourteen(SQLiteDatabase db) {
        // This process could take a while, so tell the service to wait longer
        // to make sure it can finish
        CommCareApplication.instance().setCustomServiceBindTimeout(60 * 5 * 1000);

        db.beginTransaction();
        try {
            SqlStorage<FormRecordV2> formRecordSqlStorage = new SqlStorage<>(
                    FormRecord.STORAGE_KEY,
                    FormRecordV2.class,
                    new ConcreteAndroidDbHelper(c, db));

            // Re-store all the form records, forcing new date representation
            // to be used.  Must happen proactively because the date parsing
            // code was updated to handle new representation
            for (FormRecordV2 formRecord : formRecordSqlStorage) {
                formRecordSqlStorage.write(formRecord);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFourteenFifteen(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            IndexedFixturePathUtils.createStorageBackedFixtureIndexTable(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFifteenSixteen(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            String typeFirstIndexId = "NAME_TARGET_RECORD";
            String typeFirstIndex = "name" + ", " + "case_rec_id" + ", " + "target";
            db.execSQL(DatabaseIndexingUtils.indexOnTableCommand(typeFirstIndexId, "case_index_storage", typeFirstIndex));

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Add a metadata field to all form records for "form number" that will be used for ordering
     * submissions. Since submissions were previously ordered by the last modified property,
     * set the new form numbers in this order.
     */
    private boolean upgradeSixteenSeventeen(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<FormRecordV2> oldStorage = new SqlStorage<>(
                    FormRecord.STORAGE_KEY,
                    FormRecordV2.class,
                    new ConcreteAndroidDbHelper(c, db));

            Set<String> idsOfAppsWithOldFormRecords =
                    UserDbUpgradeUtils.getAppIdsForRecords(oldStorage);
            Vector<FormRecord> upgradedRecords = new Vector<>();

            for (String appId : idsOfAppsWithOldFormRecords) {
                migrateV2FormRecordsForSingleApp(appId, oldStorage, upgradedRecords);
            }

            // Add new column to db and then write all of the new records
            UserDbUpgradeUtils.addFormNumberColumnToTable(db);
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

    /**
     * Add index on owner ID to case db
     */
    private boolean upgradeSeventeenEighteen(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL(DbUtil.addColumnToTable(
                    ACase.STORAGE_KEY,
                    "owner_id",
                    "TEXT"));

            SqlStorage<ACase> caseStorage = new SqlStorage<>(ACase.STORAGE_KEY, ACase.class,
                    new ConcreteAndroidDbHelper(c, db));
            updateModels(caseStorage);

            db.execSQL(DatabaseIndexingUtils.indexOnTableCommand(
                    "case_owner_id_index", "AndroidCase", "owner_id"));
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }


    private boolean upgradeEighteenNineteen(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<ACase> caseStorage = new SqlStorage<>(ACase.STORAGE_KEY, ACase.class,
                    new ConcreteAndroidDbHelper(c, db));

            AndroidCaseIndexTable indexTable = new AndroidCaseIndexTable(db);
            indexTable.reIndexAllCases(caseStorage);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }
    
    private boolean upgradeNineteenTwenty(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            Set<String> allIndexedFixtures = IndexedFixturePathUtils.getAllIndexedFixtureNames(db);
            for (String fixtureName : allIndexedFixtures) {
                String tableName = StorageIndexedTreeElementModel.getTableName(fixtureName);
                SqlStorage<StorageIndexedTreeElementModel> storageForThisFixture =
                        new SqlStorage<>(tableName, StorageIndexedTreeElementModel.class,
                                new ConcreteAndroidDbHelper(c, db));
                StorageIndexedTreeElementModel exampleChildElement =
                        storageForThisFixture.iterate().nextRecord();
                IndexedFixturePathUtils.buildFixtureIndices(db, tableName, exampleChildElement.getIndexColumnNames());
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }


    private boolean upgradeTwentyTwentyOne(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            UserDbUpgradeUtils.addRelationshipToAllCases(c, db);
            UserDbUpgradeUtils.migrateFormRecords(c, db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }

    }


    private void migrateV2FormRecordsForSingleApp(String appId,
                                                  SqlStorage<FormRecordV2> oldStorage,
                                                  Vector<FormRecord> upgradedRecords) {
        Vector<Integer> recordIds = oldStorage.getIDsForValue(FormRecord.META_APP_ID, appId);

        // Sort the old record ids by their last modified date, which is how form submission
        // ordering was previously done
        UserDbUpgradeUtils.sortRecordsByDate(recordIds, oldStorage);

        int submissionNumber = 0;
        for (int i = 0; i < recordIds.size(); i++) {
            FormRecordV2 oldRecord = oldStorage.read(recordIds.elementAt(i));
            FormRecord newRecord = new FormRecord(
                    oldRecord.getInstanceURIString(),
                    oldRecord.getStatus(),
                    oldRecord.getFormNamespace(),
                    oldRecord.getAesKey(),
                    oldRecord.getInstanceID(),
                    oldRecord.lastModified(),
                    oldRecord.getAppId());
            String statusOfOldRecord = oldRecord.getStatus();
            if (FormRecord.STATUS_COMPLETE.equals(statusOfOldRecord) ||
                    FormRecord.STATUS_UNSENT.equals(statusOfOldRecord)) {
                // By processing the old records in order of their last modified date, we make
                // sure that we are setting this form numbers in the most accurate order we can
                newRecord.setFormNumberForSubmissionOrdering(submissionNumber++);
            }
            newRecord.setID(oldRecord.getID());
            upgradedRecords.add(newRecord);
        }
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

}
