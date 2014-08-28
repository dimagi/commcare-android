/**
 * 
 */
package org.commcare.android.database.user;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.ConcreteDbHelper;
import org.commcare.android.database.DbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.TableBuilder;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.cases.ledger.Ledger;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public class UserDatabaseUpgrader {
    
    boolean inSenseMode = false;
    Context c;
    
    public UserDatabaseUpgrader(Context c, boolean inSenseMode) {
        this.inSenseMode = inSenseMode;
        this.c = c;
    }

    public void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion == 1) {
            if(upgradeOneTwo(db, oldVersion, newVersion)) {
                oldVersion = 2;
            }
        }
        
        if(oldVersion == 2) {
            if(upgradeTwoThree(db, oldVersion, newVersion)) {
                oldVersion = 3;
            }
        }
        
        if(oldVersion == 3) {
            if(upgradeThreeFour(db, oldVersion, newVersion)) {
                oldVersion = 4;
            }
        }
    }

    private boolean upgradeOneTwo(final SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            markSenseIncompleteUnsent(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }
    
    private boolean upgradeTwoThree(final SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            markSenseIncompleteUnsent(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }
    
    private boolean upgradeThreeFour(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();
        try {
            addStockTable(db);
            updateIndexes(db);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    private void updateIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX case_id_index ON AndroidCase (case_id)");
        db.execSQL("CREATE INDEX case_type_index ON AndroidCase (case_type)");
        db.execSQL("CREATE INDEX case_status_index ON AndroidCase (case_status)");
    }

    private void addStockTable(SQLiteDatabase db) {
        TableBuilder builder = new TableBuilder(Ledger.STORAGE_KEY);
        builder.addData(new Ledger());
        builder.setUnique(Ledger.INDEX_ENTITY_ID);
        db.execSQL(builder.getTableCreateString());
    }

    private void markSenseIncompleteUnsent(final SQLiteDatabase db) {
        //Fix for Bug in 2.7.0/1, forms in sense mode weren't being properly marked as complete after entry.
        if(inSenseMode) {
            
            //Get form record storage
            SqlStorage<FormRecord> storage = new SqlStorage<FormRecord>(FormRecord.STORAGE_KEY, FormRecord.class,new ConcreteDbHelper(c,db));
            
            //Iterate through all forms currently saved
            for(FormRecord record : storage) {
                //Update forms marked as incomplete with the appropriate status
                if(FormRecord.STATUS_INCOMPLETE.equals(record.getStatus())) {
                    //update to complete to process/send.
                    storage.write(record.updateStatus(record.getInstanceURI().toString(), FormRecord.STATUS_COMPLETE));
                }
            }                
        }
    }

}
