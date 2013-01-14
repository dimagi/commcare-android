/**
 * 
 */
package org.commcare.android.database.app;

import org.commcare.android.database.TableBuilder;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.resources.model.Resource;
import org.javarosa.core.model.instance.FormInstance;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * The helper for opening/updating the global (unencrypted) db space for CommCare.
 * 
 * 
 * 
 * @author ctsims
 *
 */
public class DatabaseAppOpenHelper extends SQLiteOpenHelper {
	
	private static final int DB_VERSION_APP = 1;
	
	private static final String DB_LOCATOR_PREF_APP = "database_app_";

	public DatabaseAppOpenHelper(Context context, String appId) {
		super(context, getDbName(appId), null, DB_VERSION_APP);
	}
	
	private static String getDbName(String appId) {
		return DB_LOCATOR_PREF_APP + appId;
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
	 */
	@Override
	public void onCreate(SQLiteDatabase database) {
		TableBuilder builder = new TableBuilder("GLOBAL_RESOURCE_TABLE");
		builder.addData(new Resource());
		database.execSQL(builder.getTableCreateString());
		
		builder = new TableBuilder("UPGRADE_RESOURCE_TABLE");
		builder.addData(new Resource());
		database.execSQL(builder.getTableCreateString());
		
		builder = new TableBuilder("fixture");
		builder.addData(new FormInstance());
		database.execSQL(builder.getTableCreateString());
		
		builder = new TableBuilder(UserKeyRecord.class);
		database.execSQL(builder.getTableCreateString());
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
	}

}
