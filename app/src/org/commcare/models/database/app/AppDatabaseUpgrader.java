package org.commcare.models.database.app;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.engine.resource.AndroidResourceManager;
import org.commcare.models.database.AndroidTableBuilder;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.app.models.UserKeyRecord;
import org.commcare.models.database.app.models.UserKeyRecordV1;
import org.commcare.models.database.migration.FixtureSerializationMigration;
import org.commcare.models.framework.Persisted;
import org.commcare.resources.model.Resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * @author ctsims
 */
class AppDatabaseUpgrader {

    private final Context context;

    public AppDatabaseUpgrader(Context context) {
        this.context = context;
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
        //NOTE: If metadata changes are made to the Resource model, they need to be
        //managed by changing the TwoThree updater to maintain that metadata.
    }

    private boolean upgradeOneTwo(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            AndroidTableBuilder builder = new AndroidTableBuilder("RECOVERY_RESOURCE_TABLE");
            builder.addData(new Resource());
            db.execSQL(builder.getTableCreateString());
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeTwoThree(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            AndroidTableBuilder builder = new AndroidTableBuilder("RECOVERY_RESOURCE_TABLE");
            builder.addData(new Resource());
            db.execSQL(builder.getTableCreateString());
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeThreeFour(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL(DatabaseAppOpenHelper.indexOnTableWithPGUIDCommand("global_index_id", "GLOBAL_RESOURCE_TABLE"));
            db.execSQL(DatabaseAppOpenHelper.indexOnTableWithPGUIDCommand("upgrade_index_id", "UPGRADE_RESOURCE_TABLE"));
            db.execSQL(DatabaseAppOpenHelper.indexOnTableWithPGUIDCommand("recovery_index_id", "RECOVERY_RESOURCE_TABLE"));
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeFourFive(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            DbUtil.createNumbersTable(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Create temporary upgrade table. Used to check for new updates without
     * wiping progress from the main upgrade table
     */
    private boolean upgradeFiveSix(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            AndroidTableBuilder builder = new AndroidTableBuilder(AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY);
            builder.addData(new Resource());
            db.execSQL(builder.getTableCreateString());
            String tableCmd =
                    DatabaseAppOpenHelper.indexOnTableWithPGUIDCommand("temp_upgrade_index_id",
                            AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY);
            db.execSQL(tableCmd);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Deserialize app fixtures in db using old form instance serialization
     * scheme, and re-serialize them using the new scheme that preserves
     * attributes.
     */
    private boolean upgradeSixSeven(SQLiteDatabase db) {
        Log.d("AppDatabaseUpgrader", "starting app fixture migration");

        FixtureSerializationMigration.stageFixtureTables(db);

        boolean didFixturesMigrate =
                FixtureSerializationMigration.migrateUnencryptedFixtureDbBytes(db, context);

        FixtureSerializationMigration.dropTempFixtureTable(db);
        return didFixturesMigrate;
    }

    /**
     * Add fields to UserKeyRecord to support PIN auth
     */
    private boolean upgradeSevenEight(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<Persisted> storage = new SqlStorage<Persisted>(
                    UserKeyRecordV1.STORAGE_KEY,
                    UserKeyRecordV1.class,
                    new ConcreteAndroidDbHelper(context, db));

            Vector<UserKeyRecord> migratedRecords = new Vector<>();
            for (Persisted record : storage) {
                UserKeyRecordV1 oldUKR = (UserKeyRecordV1)record;
                UserKeyRecord newUKR = UserKeyRecord.fromOldVersion(oldUKR);
                newUKR.setID(oldUKR.getID());
                migratedRecords.add(newUKR);
            }

            assignActiveRecords(migratedRecords);

            for (UserKeyRecord record : migratedRecords) {
                storage.write(record);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * UserKeyRecordV1 does not have the 'isActive' field (because it is being introduced with
     * this migration). Go through and mark 1 UKR per username as active, based upon the following
     * rules:
     * -If there is only 1 record for a username, mark it as active
     * -If there are multiple records for a username, mark the one with the latest validTo date as
     * active
     */
    private void assignActiveRecords(Vector<UserKeyRecord> migratedRecords) {

        // First, create a mapping from username --> list of UKRs
        Map<String, List<UserKeyRecord>> usernamesToRecords = new HashMap<>();
        for (UserKeyRecord record : migratedRecords) {
            String username = record.getUsername();
            List<UserKeyRecord> recordsForUsername = usernamesToRecords.get(username);
            if (recordsForUsername == null) {
                recordsForUsername = new ArrayList<>();
                usernamesToRecords.put(username, recordsForUsername);
            }
            recordsForUsername.add(record);
        }

        // Then determine which record for each username to mark as active
        for (String username : usernamesToRecords.keySet()) {
            List<UserKeyRecord> records = usernamesToRecords.get(username);
            UserKeyRecord activeRecord;
            if (records.size() == 1) {
                // If there is only 1 record for a username, mark it as active
                activeRecord = records.get(0);
            } else {
                // Otherwise, sort the records in decreasing order of validTo date, and then mark
                // the first one in the list as active
                Collections.sort(records, new Comparator<UserKeyRecord>() {
                    @Override
                    public int compare(UserKeyRecord lhs, UserKeyRecord rhs) {
                        return lhs.getValidTo().compareTo(rhs.getValidTo());
                    }
                });
                activeRecord = records.get(0);
            }
            activeRecord.setActive();
        }
    }
}
