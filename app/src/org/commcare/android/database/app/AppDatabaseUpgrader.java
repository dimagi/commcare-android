package org.commcare.android.database.app;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.AndroidTableBuilder;
import org.commcare.android.database.DbUtil;
import org.commcare.android.resource.AndroidResourceManager;
import org.commcare.resources.model.Resource;

/**
 * @author ctsims
 */
class AppDatabaseUpgrader {

    public AppDatabaseUpgrader() {
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

        //NOTE: If metadata changes are made to the Resource model, they need to be
        //managed by changing the TwoThree updater to maintain that metadata.
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

    private boolean upgradeOneTwo(SQLiteDatabase db, int oldVersion, int newVersion) {
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
}
