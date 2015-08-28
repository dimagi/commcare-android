/**
 *
 */
package org.commcare.android.database.global;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.ConcreteDbHelper;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.global.models.ApplicationRecordV1;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.odk.provider.ProviderUtils;
import org.javarosa.core.services.storage.Persistable;

import java.io.File;

/**
 * @author ctsims
 *
 */
public class GlobalDatabaseUpgrader {
    private Context c;

    public GlobalDatabaseUpgrader(Context c) {
        this.c = c;
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
    }

    private boolean upgradeOneTwo(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            DbUtil.createNumbersTable(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private boolean upgradeTwoThree(SQLiteDatabase db) {
        return upgradeAppRecords(db) &&
                upgradeProviderDb(db, ProviderUtils.ProviderType.FORMS) &&
                upgradeProviderDb(db, ProviderUtils.ProviderType.INSTANCES);
    }

    /**
     * Migrate all old ApplicationRecords in storage to the new version being used for multiple apps
     */
    private boolean upgradeAppRecords(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            SqlStorage<Persistable> storage = new SqlStorage<Persistable>(
                    ApplicationRecord.STORAGE_KEY,
                    ApplicationRecordV1.class,
                    new ConcreteDbHelper(c, db));
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
    private boolean upgradeProviderDb(SQLiteDatabase db, ProviderUtils.ProviderType type) {
        File oldDbFile = CommCareApplication._().getDatabasePath(type.getOldDbName());
        if (oldDbFile.exists()) {
            File newDbFile = CommCareApplication._().getDatabasePath(
                    ProviderUtils.getProviderDbName(type, getInstalledAppRecord(c, db).getApplicationId()));
            if (!oldDbFile.renameTo(newDbFile)) {
                // Big problem, should potentially crash here ?
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    private static ApplicationRecord getInstalledAppRecord(Context c, SQLiteDatabase db) {
        SqlStorage<Persistable> storage = new SqlStorage<Persistable>(
                ApplicationRecord.STORAGE_KEY,
                ApplicationRecord.class,
                new ConcreteDbHelper(c, db));
        for (Persistable p : storage) {
            ApplicationRecord r = (ApplicationRecord) p;
            if (r.getStatus() == ApplicationRecord.STATUS_INSTALLED) {
                return r;
            }
        }
        return null;
    }
}
