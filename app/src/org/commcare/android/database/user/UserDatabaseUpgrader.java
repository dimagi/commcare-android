/**
 * 
 */
package org.commcare.android.database.user;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.DbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;

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
	}
	
	private boolean upgradeOneTwo(final SQLiteDatabase db, int oldVersion, int newVersion) {
		db.beginTransaction();
		try {
			//Fix for Bug in 2.7.0/1, forms in sense mode weren't being properly marked as complete after entry.
			if(inSenseMode) {
				
				//Get form record storage
				SqlStorage<FormRecord> storage = new SqlStorage<FormRecord>(FormRecord.STORAGE_KEY, FormRecord.class, new DbHelper(c){
					@Override
					public SQLiteDatabase getHandle() {
						return db;
					}
				});
				
				//Iterate through all forms currently saved
				for(FormRecord record : storage) {
					//Update forms marked as incomplete with the appropriate status
					if(FormRecord.STATUS_INCOMPLETE.equals(record.getStatus())) {
						//update to complete to process/send.
						storage.write(record.updateStatus(record.getInstanceURI().toString(), FormRecord.STATUS_COMPLETE));
					}
				}				
			}
			db.setTransactionSuccessful();
			return true;
		} finally {
			db.endTransaction();
		}
	}

}
