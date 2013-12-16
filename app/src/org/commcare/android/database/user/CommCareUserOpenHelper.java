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
import org.commcare.android.javarosa.DeviceReportRecord;
import org.commcare.cases.stock.Stock;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.instance.FormInstance;

import android.content.Context;

/**
 * The central db point for 
 * 
 * @author ctsims
 *
 */
public class CommCareUserOpenHelper extends SQLiteOpenHelper {

	/**
	 * Version History
	 * V.4 - Added Stock table for tracking quantities. Fixed Case ID index
	 */
	private static final int USER_DB_VERSION = 4;
	
	private static final String USER_DB_LOCATOR = "database_sandbox_";
	
	private Context context;

	public CommCareUserOpenHelper(Context context, String userId) {
		super(context, getDbName(userId), null, USER_DB_VERSION);
		this.context = context;
	}
	
	public static String getDbName(String sandboxId) {
		return USER_DB_LOCATOR + sandboxId;
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
			
			builder = new TableBuilder(DeviceReportRecord.class);
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder("fixture");
			builder.addData(new FormInstance());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(Stock.STORAGE_KEY);
			builder.addData(new Stock());
			builder.setUnique(Stock.INDEX_ENTITY_ID);
			database.execSQL(builder.getTableCreateString());
			
			//The uniqueness index should be doing this for us
			database.execSQL("CREATE INDEX case_id_index ON AndroidCase (case_id)");
			database.execSQL("CREATE INDEX case_type_index ON AndroidCase (case_type)");
			database.execSQL("CREATE INDEX case_status_index ON AndroidCase (case_status)");
			
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
		boolean inSenseMode = false;
		//TODO: Not a great way to get the current app! Pass this in to the constructor.
		//I am preeeeeety sure that we can't get here without _having_ an app/platform, but not 100%
		try {
			if(CommCareApplication._().getCommCarePlatform() != null && CommCareApplication._().getCommCarePlatform().getCurrentProfile() != null) {
				if(CommCareApplication._().getCommCarePlatform().getCurrentProfile() != null && 
				   CommCareApplication._().getCommCarePlatform().getCurrentProfile().isFeatureActive("sense")) {
		    		inSenseMode = true;
		    	} 
			} else {
				//Hold off on update?
			}
		} catch(Exception e) {
			
		}
		new UserDatabaseUpgrader(context, inSenseMode).upgrade(db, oldVersion, newVersion);
	}

}
