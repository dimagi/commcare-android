package org.commcare.models.database.global;

import android.content.Context;

import java.io.File;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.AppAvailableToInstall;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.global.models.ApplicationRecordV1;
import org.commcare.android.database.global.models.ConnectKeyRecord;
import org.commcare.android.database.global.models.ConnectKeyRecordV6;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.android.logging.ForceCloseLogEntry;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.DbUtil;
import org.commcare.models.database.IDatabase;
import org.commcare.models.database.MigrationException;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.database.TableBuilder;
import org.commcare.provider.ProviderUtils;

import org.javarosa.core.services.storage.Persistable;

/**
 * @author ctsims
 */
class GlobalDatabaseUpgrader {
    private final Context c;

    public GlobalDatabaseUpgrader(Context c) {
        this.c = c;
    }

    public void upgrade(IDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1) {
            if (upgradeOneTwo(db, oldVersion, newVersion)) {
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
    }

    private boolean upgradeOneTwo(IDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            DbUtil.createNumbersTable(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeTwoThree(IDatabase db) {
        db.beginTransaction();

        //First, migrate the old ApplicationRecord in storage to the new version being used for
        // multiple apps.
        try {
            SqlStorage<Persistable> storage = new SqlStorage<Persistable>(
                    ApplicationRecord.STORAGE_KEY,
                    ApplicationRecordV1.class,
                    new ConcreteAndroidDbHelper(c, db));

            if (multipleInstalledAppRecords(storage)) {
                // If a device has multiple installed ApplicationRecords before the multiple apps
                // db upgrade has occurred, something has definitely gone wrong
                throw new MigrationException(true);
            }

            for (Persistable r : storage) {
                ApplicationRecordV1 oldRecord = (ApplicationRecordV1) r;
                ApplicationRecord newRecord =
                        new ApplicationRecord(oldRecord.getApplicationId(), oldRecord.getStatus());
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                //set default values for the new fields
                newRecord.setResourcesStatus(true);
                newRecord.setArchiveStatus(false);
                newRecord.setUniqueId("");
                newRecord.setDisplayName("");
                newRecord.setVersionNumber(-1);
                newRecord.setConvertedByDbUpgrader(true);
                newRecord.setPreMultipleAppsProfile(true);
                storage.write(newRecord);
            }

            // Then migrate the databases for both providers
            if (upgradeProviderDb(db, ProviderUtils.ProviderType.FORMS) &&
                    upgradeProviderDb(db, ProviderUtils.ProviderType.INSTANCES)) {
                db.setTransactionSuccessful();
                return true;
            }
            return false;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeThreeFour(IDatabase db) {
        return addTableForNewModel(db, ForceCloseLogEntry.STORAGE_KEY, new ForceCloseLogEntry());
    }

    private boolean upgradeFourFive(IDatabase db) {
        return addTableForNewModel(db, AppAvailableToInstall.STORAGE_KEY, new AppAvailableToInstall());
    }

    private boolean upgradeFiveSix(IDatabase db) {
        return addTableForNewModel(db, ConnectKeyRecord.STORAGE_KEY, new ConnectKeyRecordV6());
    }

    private boolean upgradeSixSeven(IDatabase db) {
        db.beginTransaction();

        //Migrate the old ConnectKeyRecord in storage to the new version including isLocal
        try {
            db.execSQL(DbUtil.addColumnToTable(
                    ConnectKeyRecord.STORAGE_KEY,
                    ConnectKeyRecord.IS_LOCAL,
                    "TEXT"));

            SqlStorage<Persistable> oldStorage = new SqlStorage<>(
                    ConnectKeyRecordV6.STORAGE_KEY,
                    ConnectKeyRecordV6.class,
                    new ConcreteAndroidDbHelper(c, db));

            SqlStorage<Persistable> newStorage = new SqlStorage<>(
                    ConnectKeyRecord.STORAGE_KEY,
                    ConnectKeyRecord.class,
                    new ConcreteAndroidDbHelper(c, db));

            for (Persistable r : oldStorage) {
                ConnectKeyRecordV6 oldRecord = (ConnectKeyRecordV6)r;
                ConnectKeyRecord newRecord = ConnectKeyRecord.fromV6(oldRecord);
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                newStorage.write(newRecord);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeSevenEight(IDatabase db) {
        return addTableForNewModel(db, GlobalErrorRecord.STORAGE_KEY, new GlobalErrorRecord());
    }

    private static boolean addTableForNewModel(IDatabase db, String storageKey,
                                               Persistable modelToAdd) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(storageKey);
            builder.addData(modelToAdd);
            db.execSQL(builder.getTableCreateString());

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Prior to multiple application seating, the FormsProvider and the InstanceProvider were both
     * using one global database for all forms/instances. Now that we can have multiple apps
     * installed at once, we need to have one forms db and one instances db per app. This method
     * performs the necessary one-time migration from a global db that still exists on a device
     * to the new per-app system
     */
    private boolean upgradeProviderDb(IDatabase db, ProviderUtils.ProviderType type) {
        File oldDbFile = CommCareApplication.instance().getDatabasePath(type.getOldDbName());
        if (oldDbFile.exists()) {
            File newDbFile = CommCareApplication.instance().getDatabasePath(
                    ProviderUtils.getProviderDbName(type, getInstalledAppRecord(c, db).getApplicationId()));
            if (!oldDbFile.renameTo(newDbFile)) {
                throw new MigrationException(false);
            } else {
                return true;
            }
        }
        return true;
    }

    private static boolean multipleInstalledAppRecords(SqlStorage<Persistable> storage) {
        int count = 0;
        for (Persistable p : storage) {
            ApplicationRecordV1 r = (ApplicationRecordV1) p;
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
                count++;
            }
        }
        return (count > 1);
    }

    private static ApplicationRecord getInstalledAppRecord(Context c, IDatabase db) {
        SqlStorage<Persistable> storage = new SqlStorage<Persistable>(
                ApplicationRecord.STORAGE_KEY,
                ApplicationRecord.class,
                new ConcreteAndroidDbHelper(c, db));
        for (Persistable p : storage) {
            ApplicationRecord r = (ApplicationRecord) p;
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
                return r;
            }
        }
        return null;
    }

}
