package org.commcare.android.database.app;

import android.content.Context;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.DbUtil;
import org.commcare.android.database.TableBuilder;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.resources.model.Resource;

/**
 * @author ctsims
 */
public class AppDatabaseUpgrader {
    private Context c;
    
    public AppDatabaseUpgrader(Context c) {
        this.c = c;
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion == 1) {
            if(upgradeOneTwo(db, oldVersion, newVersion)) {
                oldVersion = 2;
            }
        }
        if(oldVersion == 2) {
            if(upgradeTwoThree(db)) {
                oldVersion = 3;
            }
        }
        if(oldVersion == 3) {
            if(upgradeThreeFour(db)) {
                oldVersion = 4;
            }
        } 
        
        if(oldVersion == 4) {
            if(upgradeFourFive(db)) {
                oldVersion = 5;
            }
        } 

        //NOTE: If metadata changes are made to the Resource model, they need to be
        //managed by changing the TwoThree updater to maintain that metadata.
    }
    


    private boolean upgradeTwoThree(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            TableBuilder builder = new TableBuilder(CommCareApp.RECOVERY_STORAGE_TABLE_KEY);
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
            TableBuilder builder = new TableBuilder(CommCareApp.RECOVERY_STORAGE_TABLE_KEY);
            builder.addData(new Resource());
            db.execSQL(builder.getTableCreateString());
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private  boolean upgradeThreeFour(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL("CREATE INDEX global_index_id ON " + 
                    CommCareApp.GLOBAL_STORAGE_TABLE_KEY +
                    " ( " + Resource.META_INDEX_PARENT_GUID + " )");
            db.execSQL("CREATE INDEX upgrade_index_id ON " + CommCareApp.UPGRADE_STORAGE_TABLE_KEY +
                    " ( " + Resource.META_INDEX_PARENT_GUID + " )");
            db.execSQL("CREATE INDEX recovery_index_id ON " + CommCareApp.RECOVERY_STORAGE_TABLE_KEY +
                    " ( " + Resource.META_INDEX_PARENT_GUID + " )");
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
}
