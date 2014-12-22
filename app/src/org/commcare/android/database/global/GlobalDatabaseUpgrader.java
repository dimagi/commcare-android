/**
 * 
 */
package org.commcare.android.database.global;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.DbUtil;

import android.content.Context;

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
        if(oldVersion == 1) {
            if(upgradeOneTwo(db, oldVersion, newVersion)) {
                oldVersion = 2;
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
}
