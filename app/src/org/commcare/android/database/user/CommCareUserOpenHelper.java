/**
 * 
 */
package org.commcare.android.database.user;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import org.commcare.android.database.TableBuilder;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.database.user.models.GeocodeCacheModel;
import org.commcare.android.database.user.models.SessionStateDescriptor;
import org.commcare.android.database.user.models.User;
import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.DeviceReportRecord;
import org.javarosa.core.model.instance.FormInstance;

import android.content.Context;

/**
 * The central db point for 
 * 
 * @author ctsims
 *
 */
public class CommCareUserOpenHelper extends SQLiteOpenHelper {

	private static final int USER_DB_VERSION = 1;
	
	private static final String USER_DB_LOCATOR = "database_user";


	
	public CommCareUserOpenHelper(Context context, String userId) {
		super(context, getDbName(userId), null, USER_DB_VERSION);
	}
	
	private static String getDbName(String userId) {
		return USER_DB_LOCATOR + userId;
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
			
			builder = new TableBuilder("USER");
			builder.addData(new User());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(FormRecord.class);
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(SessionStateDescriptor.class);
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(GeocodeCacheModel.STORAGE_KEY);
			builder.addData(new GeocodeCacheModel());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(AndroidLogEntry.STORAGE_KEY);
			builder.addData(new AndroidLogEntry());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(DeviceReportRecord.class);
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder("fixture");
			builder.addData(new FormInstance());
			database.execSQL(builder.getTableCreateString());
			
			database.setVersion(USER_DB_VERSION);
					
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub

	}

}
