/**
 * 
 */
package org.commcare.android.util;

import org.commcare.android.database.TableBuilder;
import org.commcare.android.database.cache.GeocodeCacheModel;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.SessionStateDescriptor;
import org.commcare.resources.model.Resource;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

/**
 * 
 * This class exists in order to handle all of the logic associated with upgrading from one version
 * of CommCare ODK to another. It is going to get big and annoying.
 * 
 * @author ctsims
 */
public class CommCareUpgrader {
	
	Context context;
	
	public CommCareUpgrader(Context c) {
		this.context = c;
	}
	
	public boolean doUpgrade(SQLiteDatabase database, int from, int to) {
		if(from == 1) {
			if(upgradeOneTwo(database)) {
				from = 2;
			} else { return false;}
		}
		
		if(from == 26) {
			if(upgradeTwoSixtoTwoSeven(database)) {
				from = 27;
			} else { return false;}
		}
		
		if(from == 27) {
			if(upgradeTwoSeventoTwoEight(database)) {
				from = 28;
			} else { return false;}
		} 
		
		if(from == 28) {
			if(upgradeTwoEighttoTwoNine(database)) {
				from = 29;
			} else { return false; }
		}
		
		return from == to; 
	}


	public boolean upgradeOneTwo(SQLiteDatabase database) {
		database.beginTransaction();
		TableBuilder builder = new TableBuilder("UPGRADE_RESOURCE_TABLE");
		builder.addData(new Resource());
		database.execSQL(builder.getTableCreateString());
		
		database.setVersion(2);
		database.setTransactionSuccessful();
		database.endTransaction();
		return true;
	}
	
	/**
	 * Due a to a bug in Android 2.2 that is present on ~all of our 
	 * production phones, upgrading from database versions
	 * before v. TOBEDECIDED is going to toast all of the app data. As such
	 * we need to remove all of the relevant records to ensure that
	 * the app doesn't crash and burn. 
	 * 
	 * @param database
	 */
	public void upgradeBeforeTwentyFour(SQLiteDatabase database) {
		//NOTE: We'll do this cleanly when appropriate, but for
		//right now we're just going to live with the bug.
		
		database.beginTransaction();
		
		database.execSQL("delete from GLOBAL_RESOURCE_TABLE");
		database.execSQL("delete from UPGRADE_RESOURCE_TABLE");
		database.execSQL("delete from " + FormRecord.STORAGE_KEY);
		database.setTransactionSuccessful();
		database.endTransaction();
	}
	
	/**
	 * Previous FormRecord entries were lacking, we're going to 
	 * wipe them out.
	 * 
	 * @param database 
	 * @return
	 */
	private boolean upgradeTwoSixtoTwoSeven(SQLiteDatabase database) {
		database.beginTransaction();
		
		//wipe out old Form Record table
		database.execSQL("drop table " + FormRecord.STORAGE_KEY);
		
		//Build us a new one with the new structure
		TableBuilder builder = new TableBuilder(FormRecord.STORAGE_KEY);
		builder.addData(new FormRecord());
		database.execSQL(builder.getTableCreateString());

		database.setTransactionSuccessful();
		database.endTransaction();
		return true;
	}
	
	
	private boolean upgradeTwoSeventoTwoEight(SQLiteDatabase database) {
		database.beginTransaction();
		
		TableBuilder builder = new TableBuilder(GeocodeCacheModel.STORAGE_KEY);
		builder.addData(new GeocodeCacheModel());
		database.execSQL(builder.getTableCreateString());

		database.setTransactionSuccessful();
		database.endTransaction();
		return true;
	}
	
	private boolean upgradeTwoEighttoTwoNine(SQLiteDatabase database) {
		try {
			database.beginTransaction();
			
			TableBuilder builder = new TableBuilder(AndroidLogEntry.STORAGE_KEY);
			builder.addData(new AndroidLogEntry());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(DeviceReportRecord.STORAGE_KEY);
			builder.addData(new DeviceReportRecord());
			database.execSQL(builder.getTableCreateString());
			
			//SQLite can't add column constraints. You've gotta make a new table, copy everything over, and 
			//wipe the old one
			
			String table = TableBuilder.scrubName(SessionStateDescriptor.STORAGE_KEY);
			String tempTable = TableBuilder.scrubName(SessionStateDescriptor.STORAGE_KEY + "temp");
			
			database.execSQL(String.format("ALTER TABLE %s RENAME TO %s;", table, tempTable));
			
			builder = new TableBuilder(SessionStateDescriptor.STORAGE_KEY);
			builder.setUnique(SessionStateDescriptor.META_FORM_RECORD_ID);
			builder.addData(new SessionStateDescriptor());
			database.execSQL(builder.getTableCreateString());
			
			String cols = builder.getColumns();
			
			database.execSQL(String.format("INSERT OR REPLACE INTO %s (%s) " +
							"SELECT %s " +
							"FROM %s;", table, cols, cols, tempTable));
			
			database.execSQL(String.format("DROP TABLE %s;", tempTable));
					
			database.setTransactionSuccessful();
			return true;
		} finally {
			database.endTransaction();
		}
	}

}
