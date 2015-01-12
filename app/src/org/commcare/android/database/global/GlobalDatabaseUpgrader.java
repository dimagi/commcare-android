/**
 * 
 */
package org.commcare.android.database.global;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.ConcreteDbHelper;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.database.global.models.ApplicationRecordV1;
import org.javarosa.core.services.storage.Persistable;

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
        if (oldVersion == 2) {
            if(upgradeTwoThree(db, oldVersion, newVersion)) {
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
    
    private boolean upgradeTwoThree(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            SqlStorage<Persistable> storage = new SqlStorage<Persistable>(ApplicationRecord.STORAGE_KEY, ApplicationRecordV1.class, new ConcreteDbHelper(c, db));
            for (int i = 0; i < storage.getNumRecords(); i++) {
                ApplicationRecordV1 oldRecord = (ApplicationRecordV1)storage.read(i);
                ApplicationRecord newRecord = new ApplicationRecord(oldRecord.getApplicationId(), oldRecord.getStatus());
                //set this new record to have same ID as the old one
                newRecord.setID(oldRecord.getID());
                //set default values for the new fields
                newRecord.setResourcesStatus(true);
                newRecord.setArchiveStatus(false);
                newRecord.setUniqueId(null);
                newRecord.setDisplayName(null);
                storage.write(newRecord);
            }
            return true;
        } finally {
            db.endTransaction();
        }
    }
    
    
}
