package org.commcare.models.database.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.app.models.FormDefRecordV12;
import org.commcare.android.database.app.models.ResourceV13;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.app.models.UserKeyRecordV1;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.android.resource.installers.XFormAndroidInstallerV8;
import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.AndroidPrototypeFactoryV8;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.migration.FixtureSerializationMigration;
import org.commcare.modern.database.TableBuilder;
import org.commcare.provider.FormsProviderAPI;
import org.commcare.recovery.measures.RecoveryMeasure;
import org.commcare.resources.model.Resource;
import org.commcare.util.LogTypes;
import org.commcare.utils.GlobalConstants;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import static org.commcare.engine.resource.AndroidResourceManager.TEMP_UPGRADE_TABLE_KEY;
import static org.commcare.utils.AndroidCommCarePlatform.GLOBAL_RESOURCE_TABLE_NAME;
import static org.commcare.utils.AndroidCommCarePlatform.RECOVERY_RESOURCE_TABLE_NAME;
import static org.commcare.utils.AndroidCommCarePlatform.UPGRADE_RESOURCE_TABLE_NAME;

/**
 * @author ctsims
 */
class AppDatabaseUpgrader {

    private final Context context;

    AppDatabaseUpgrader(Context context) {
        this.context = context;
    }

    public void upgrade(IDatabase db, int oldVersion, int newVersion) {
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
            if (upgradeEightTen(db)) {
                oldVersion = 10;
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

        //NOTE: If metadata changes are made to the Resource model, they need to be
        //managed by changing the TwoThree updater to maintain that metadata.
    }


    private boolean upgradeOneTwo(IDatabase db) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder("RECOVERY_RESOURCE_TABLE");
            builder.addData(new ResourceV13());
            db.execSQL(builder.getTableCreateString());
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeTwoThree(IDatabase db) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder("RECOVERY_RESOURCE_TABLE");
            builder.addData(new ResourceV13());
            db.execSQL(builder.getTableCreateString());
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeThreeFour(IDatabase db) {
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

    private boolean upgradeFourFive(IDatabase db) {
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
    private boolean upgradeFiveSix(IDatabase db) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(TEMP_UPGRADE_TABLE_KEY);
            builder.addData(new ResourceV13());
            db.execSQL(builder.getTableCreateString());
            String tableCmd =
                    DatabaseAppOpenHelper.indexOnTableWithPGUIDCommand("temp_upgrade_index_id",
                            TEMP_UPGRADE_TABLE_KEY);
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
    private boolean upgradeSixSeven(IDatabase db) {
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
    private boolean upgradeSevenEight(IDatabase db) {
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
    private boolean upgradeEightTen(IDatabase db) {
        boolean success;
        db.beginTransaction();
        try {
            upgradeXFormAndroidInstallerV8(GLOBAL_RESOURCE_TABLE_NAME, db);
            upgradeXFormAndroidInstallerV8(UPGRADE_RESOURCE_TABLE_NAME, db);
            upgradeXFormAndroidInstallerV8(RECOVERY_RESOURCE_TABLE_NAME, db);

            // Create FormDef table
            TableBuilder builder = new TableBuilder(FormDefRecordV12.class);
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

    // I only exist since there was a time when there were no Upgrade and Recovery table in v8-v9 migration
    private boolean upgradeNineTen(IDatabase db) {
        db.beginTransaction();
        try {
            upgradeXFormAndroidInstallerV9(UPGRADE_RESOURCE_TABLE_NAME, db);
            upgradeXFormAndroidInstallerV9(RECOVERY_RESOURCE_TABLE_NAME, db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return true;
    }

    // Corrects 'update' file references for FormDef Records that didn't
    // change to 'install' path because of an earlier bug
    private boolean upgradeTenEleven(IDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<FormDefRecordV12> formDefRecordStorage = new SqlStorage<>(
                    FormDefRecord.STORAGE_KEY,
                    FormDefRecordV12.class,
                    new ConcreteAndroidDbHelper(context, db));
            for (FormDefRecordV12 formDefRecord : formDefRecordStorage) {
                String filePath = formDefRecord.getFilePath();
                File formFile = new File(filePath);

                // update the path for the record if it points to a non existent upgrade path and corresponding install path exists
                if (!formFile.exists() && filePath.contains(GlobalConstants.FILE_CC_UPGRADE)) {
                    String newFilePath = filePath.replace(GlobalConstants.FILE_CC_UPGRADE, GlobalConstants.FILE_CC_INSTALL + "/");
                    if (new File(newFilePath).exists()) {
                        formDefRecord.updateFilePath(formDefRecordStorage, newFilePath);
                    } else {
                        Logger.log(LogTypes.SOFT_ASSERT, "File not found at both upgrade and install path for form " + formDefRecord.getJrFormId());
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return true;
    }

    private boolean upgradeElevenTwelve(IDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL(new TableBuilder(RecoveryMeasure.class).getTableCreateString());
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }


    private boolean upgradeTwelveThirteen(IDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL(DbUtil.addColumnToTable(
                    FormDefRecord.STORAGE_KEY,
                    FormDefRecord.META_RESOURCE_VERSION,
                    "INTEGER"));

            SqlStorage<FormDefRecordV12> oldFormDefRecordStorage = new SqlStorage<>(
                    FormDefRecord.STORAGE_KEY,
                    FormDefRecordV12.class,
                    new ConcreteAndroidDbHelper(context, db));

            SqlStorage<FormDefRecord> formDefRecordStorage = new SqlStorage<>(
                    FormDefRecord.STORAGE_KEY,
                    FormDefRecord.class,
                    new ConcreteAndroidDbHelper(context, db));

            for (FormDefRecordV12 oldFormDefRecord : oldFormDefRecordStorage) {
                FormDefRecord formDefRecord = new FormDefRecord(oldFormDefRecord);
                formDefRecordStorage.update(oldFormDefRecord.getID(), formDefRecord);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeThirteenFourteen(IDatabase db) {
        db.beginTransaction();
        try {
            upgradeResourcesV13(db, GLOBAL_RESOURCE_TABLE_NAME);
            upgradeResourcesV13(db, UPGRADE_RESOURCE_TABLE_NAME);
            upgradeResourcesV13(db, RECOVERY_RESOURCE_TABLE_NAME);
            upgradeResourcesV13(db, TEMP_UPGRADE_TABLE_KEY);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private void upgradeResourcesV13(IDatabase db, String tableName) {
        db.beginTransaction();
        try {

            db.execSQL(DbUtil.addColumnToTable(
                    tableName,
                    Resource.META_INDEX_LAZY,
                    "TEXT",
                    "false"));

            SqlStorage<ResourceV13> resourceStorage = new SqlStorage<>(
                    tableName,
                    ResourceV13.class,
                    new ConcreteAndroidDbHelper(context, db));

            Vector<Resource> updateResourceList = new Vector<>();

            for (ResourceV13 oldResource : resourceStorage) {
                Resource newResource = new Resource(oldResource.getVersion(), oldResource.getResourceId(), oldResource.getLocations(), oldResource.getDescriptor(), Resource.LAZY_VAL_FALSE);
                newResource.setStatus(oldResource.getStatus());
                newResource.setRecordGuid(oldResource.getRecordGuid());
                newResource.setID(oldResource.getID());
                newResource.setInstaller(oldResource.getInstaller());
                updateResourceList.add(newResource);
            }

            SqlStorage<Resource> newResourceStorage = new SqlStorage<>(
                    tableName,
                    Resource.class,
                    new ConcreteAndroidDbHelper(context, db));
            for (Resource resource : updateResourceList) {
                newResourceStorage.update(resource.getID(), resource);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

    }

    // migrate formProvider entries to db
    private void migrateFormProvider(IDatabase db) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(FormsProviderAPI.FormsColumns.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                SqlStorage<FormDefRecordV12> formDefRecordStorage = new SqlStorage<>(
                        FormDefRecord.STORAGE_KEY,
                        FormDefRecordV12.class,
                        new ConcreteAndroidDbHelper(context, db));
                while (cursor.moveToNext()) {
                    FormDefRecordV12 formDefRecord = new FormDefRecordV12(cursor);
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

    /**
     * There can be 2 different configurations for resource table here
     * 1. Either the v8-v9 upgrade failed and rsources are in v8 state
     * 2. v8-v9 upgrade was successful and resources are already in v9/v10 state (Resources downloaded after the update)
     *
     * So we wanna assume 1 and try updating the resources to v10,
     * if above fails, we are checking for 2 i.e if resources are in v10 state.
     * If they are not we are going to wipe the table
     *
     * @param tableName Resource table that need to be upgraded
     * @param db        App DB
     */
    private void upgradeXFormAndroidInstallerV9(String tableName, IDatabase db) {
        // Safe checking against calling this method by mistake for Global table
        if (tableName.contentEquals(GLOBAL_RESOURCE_TABLE_NAME)) {
            return;
        }

        try {
            upgradeXFormAndroidInstallerV8(tableName, db);
        } catch (Exception e) {
            try {
                SqlStorage<ResourceV13> newResourceStorage = new SqlStorage<>(
                        tableName,
                        ResourceV13.class,
                        new ConcreteAndroidDbHelper(context, db));
                for (ResourceV13 resource : newResourceStorage) {
                    // Do nothing, just checking if we can read all resources successfully
                    // signifying that they are already following new v10 model
                }
            } catch (Exception ex) {
                SqlStorage.wipeTable(db, tableName);
                Logger.log(LogTypes.SOFT_ASSERT, "Wiped table on upgrade " + tableName);
            }
        }
    }


    private void upgradeXFormAndroidInstallerV8(String tableName, IDatabase db) {
        db.beginTransaction();
        try {
            // Get Global Resource Storage using AndroidPrototypeFactoryV8
            SqlStorage<ResourceV13> oldResourceStorage = new SqlStorage<>(
                    tableName,
                    ResourceV13.class,
                    new ConcreteAndroidDbHelper(context, db) {
                        @Override
                        public PrototypeFactory getPrototypeFactory() {
                            return AndroidPrototypeFactoryV8.getAndroidPrototypeFactoryV8(c);
                        }
                    });

            Vector<ResourceV13> updateResourceList = new Vector<>();

            // If Resource Installer is of Type XFormAndroidInstallerV8 , update it to XFormAndroidInstaller
            // and add resource record to the updateResourceList
            for (ResourceV13 resource : oldResourceStorage) {
                if (resource.getInstaller() instanceof XFormAndroidInstallerV8) {
                    XFormAndroidInstallerV8 oldInstaller = (XFormAndroidInstallerV8)resource.getInstaller();
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
            SqlStorage<ResourceV13> newResourceStorage = new SqlStorage<>(
                    tableName,
                    ResourceV13.class,
                    new ConcreteAndroidDbHelper(context, db));
            for (ResourceV13 resource : updateResourceList) {
                newResourceStorage.update(resource.getID(), resource);
            }
            db.setTransactionSuccessful();
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
                Collections.sort(records, (lhs, rhs) -> lhs.getValidTo().compareTo(rhs.getValidTo()));
                activeRecord = records.get(0);
            }
            activeRecord.setActive();
        }
    }
}
