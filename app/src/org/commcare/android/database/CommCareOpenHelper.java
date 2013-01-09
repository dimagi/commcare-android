/**
 * 
 */
package org.commcare.android.database;

import org.commcare.android.database.cache.GeocodeCacheModel;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.ACase;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.SessionStateDescriptor;
import org.commcare.android.models.User;
import org.commcare.android.util.CommCareUpgrader;
import org.commcare.resources.model.Resource;
import org.javarosa.core.model.instance.FormInstance;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @author ctsims
 *
 */
public class CommCareOpenHelper extends SQLiteOpenHelper {
	
	
	/*
	 * Version History:
	 * 28 - Added the geocaching table
	 * 29 - Added Logging table. Made SSD FormRecord_ID unique
	 * 30 - Added validation, need to pre-flag validation
	 */
	
    private static final int DATABASE_VERSION = 30;
    private Context context;
    
    public CommCareOpenHelper(Context context) {
    	this(context, null);
    }

	public CommCareOpenHelper(Context context, CursorFactory factory) {
        super(context, GlobalConstants.CC_DB_NAME, factory, DATABASE_VERSION);
        this.context = context;
	}
	
	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
	 */
	@Override
	public void onCreate(SQLiteDatabase database) {
		
		try {
			database.beginTransaction();
			
			TableBuilder builder = new TableBuilder(ACase.STORAGE_KEY);
			builder.addData(new ACase());
			builder.setUnique(ACase.INDEX_CASE_ID);
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(User.STORAGE_KEY);
			builder.addData(new User());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder("GLOBAL_RESOURCE_TABLE");
			builder.addData(new Resource());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder("UPGRADE_RESOURCE_TABLE");
			builder.addData(new Resource());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(FormRecord.STORAGE_KEY);
			builder.addData(new FormRecord());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(SessionStateDescriptor.STORAGE_KEY);
			builder.setUnique(SessionStateDescriptor.META_FORM_RECORD_ID);
			builder.addData(new SessionStateDescriptor());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder("fixture");
			builder.addData(new FormInstance());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(GeocodeCacheModel.STORAGE_KEY);
			builder.addData(new GeocodeCacheModel());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(AndroidLogEntry.STORAGE_KEY);
			builder.addData(new AndroidLogEntry());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(DeviceReportRecord.STORAGE_KEY);
			builder.addData(new DeviceReportRecord());
			database.execSQL(builder.getTableCreateString());
			
			
			database.setVersion(DATABASE_VERSION);
					
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		CommCareUpgrader upgrader = new CommCareUpgrader(context);
		
		//Evaluate success here somehow. Also, we'll need to log in to
		//mess with anything in the DB, or any old encrypted files, we need a hook for that...
		upgrader.doUpgrade(database, oldVersion, newVersion);
	}

}
