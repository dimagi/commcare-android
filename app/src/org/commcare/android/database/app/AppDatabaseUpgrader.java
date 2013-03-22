/**
 * 
 */
package org.commcare.android.database.app;

import org.commcare.android.database.TableBuilder;
import org.commcare.resources.model.Resource;

import net.sqlcipher.database.SQLiteDatabase;

/**
 * @author ctsims
 *
 */
public class AppDatabaseUpgrader {

	public static void upgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if(oldVersion == 1) {
			if(upgradeOneTwo(db, oldVersion, newVersion)) {
				oldVersion = 2;
			}
		}
	}
	
	private static boolean upgradeOneTwo(SQLiteDatabase db, int oldVersion, int newVersion) {
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

}
