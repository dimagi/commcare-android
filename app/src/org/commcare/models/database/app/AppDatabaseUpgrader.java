package org.commcare.models.database.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.commons.lang3.StringUtils;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.app.models.UserKeyRecordV1;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.android.resource.installers.XFormAndroidInstallerV1;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.engine.resource.AndroidResourceManager;
import org.commcare.models.AndroidPrototypeFactoryV1;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.migration.FixtureSerializationMigration;
import org.commcare.modern.database.TableBuilder;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.resources.model.Resource;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.PrototypeFactory;

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

        if (oldVersion == 8) {
            if (upgradeEightNine(db)) {
                oldVersion = 9;
            }
        }
        //NOTE: If metadata changes are made to the Resource model, they need to be
        //managed by changing the TwoThree updater to maintain that metadata.
    }

    private boolean upgradeOneTwo(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder("RECOVERY_RESOURCE_TABLE");
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
            TableBuilder builder = new TableBuilder("RECOVERY_RESOURCE_TABLE");
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
            TableBuilder builder = new TableBuilder(AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY);
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
            SqlStorage<Persisted> storage = new SqlStorage<>(
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

    // Migrate records form FormProvider and InstanceProvider to new FormDefRecord and FormRecord respectively
    private boolean upgradeEightNine(SQLiteDatabase db) {
        boolean success;
        db.beginTransaction();
        try {
            upgradeXFormAndroidInstallerV1(db);

            // Create FormDef table
            TableBuilder builder = new TableBuilder(FormDefRecord.class);
            db.execSQL(builder.getTableCreateString());

            migrateFormProvider(db);
            db.setTransactionSuccessful();
            success = true;
        } finally {
            db.endTransaction();
        }

        // Delete entries from FormsProvider if migration has been successful
        if (success) {
            try {
                context.getContentResolver().delete(FormsProviderAPI.FormsColumns.CONTENT_URI, null, null);
            } catch (Exception e) {
                // Failure here won't cause any problems in app operations. So fail silently.
                e.printStackTrace();
                Logger.exception("Error while deleting FormsProvider entries during app db migration", e);
            }
        }
        return success;
    }

    // migrate formProvider entries to db
    private void migrateFormProvider(SQLiteDatabase db) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(FormsProviderAPI.FormsColumns.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                SqlStorage<FormDefRecord> formDefRecordStorage = new SqlStorage<>(
                        FormDefRecord.STORAGE_KEY,
                        FormDefRecord.class,
                        new ConcreteAndroidDbHelper(context, db));
                while (cursor.moveToNext()) {
                    FormDefRecord formDefRecord = new FormDefRecord(cursor);
                    formDefRecord.save(formDefRecordStorage);
                }
            }
        } finally {
            safeCloseCursor(cursor);
        }
    }

    private void safeCloseCursor(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }

    private void upgradeXFormAndroidInstallerV1(SQLiteDatabase db) {
        // Get Global Resource Storage using AndroidPrototypeFactoryV1
        SqlStorage<Resource> oldGlobalResourceStorage = new SqlStorage<>(
                "GLOBAL_RESOURCE_TABLE",
                Resource.class,
                new ConcreteAndroidDbHelper(context, db) {
                    @Override
                    public PrototypeFactory getPrototypeFactory() {
                        return AndroidPrototypeFactoryV1.getAndroidPrototypeFactoryV1(c);
                    }
                });

        Vector<Resource> updateResourceList = new Vector<>();

        // If Resource Installer is of Type XFormAndroidInstallerV1 , update it to XFormAndroidInstaller
        // and add resource record to the updateResourceList
        for (Resource resource : oldGlobalResourceStorage) {
            if (resource.getInstaller() instanceof XFormAndroidInstallerV1) {
                XFormAndroidInstallerV1 oldInstaller = (XFormAndroidInstallerV1)resource.getInstaller();
                String contentUri = oldInstaller.getContentUri();
                int formDefId = -1;
                if (!StringUtils.isEmpty(contentUri)) {
                    formDefId = Integer.valueOf(Uri.parse(contentUri).getLastPathSegment());
                }
                XFormAndroidInstaller newInstaller = new XFormAndroidInstaller(
                        oldInstaller.getLocalLocation(),
                        oldInstaller.getLocalDestination(),
                        oldInstaller.getUpgradeDestination(),
                        oldInstaller.getNamespace(),
                        formDefId);
                resource.setInstaller(newInstaller);
                updateResourceList.add(resource);
            }
        }

        // Rewrite the records in updateResourceList using the standard AndroidProtoTypeFactory
        SqlStorage<Resource> newGlobalResourceStorage = new SqlStorage<>(
                "GLOBAL_RESOURCE_TABLE",
                Resource.class,
                new ConcreteAndroidDbHelper(context, db));
        for (Resource resource : updateResourceList) {
            newGlobalResourceStorage.update(resource.getID(), resource);
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
