/**
 * 
 */
package org.commcare.android.database;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.Case;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.Referral;
import org.commcare.android.models.User;
import org.commcare.android.util.CommCareUpgrader;
import org.commcare.resources.model.Resource;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @author ctsims
 *
 */
public class CommCareOpenHelper extends SQLiteOpenHelper {
	
    private static final int DATABASE_VERSION = 25;
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
			
			TableBuilder builder = new TableBuilder(Case.STORAGE_KEY);
			builder.addData(new Case());
			database.execSQL(builder.getTableCreateString());
			
			builder = new TableBuilder(Referral.STORAGE_KEY);
			builder.addData(new Referral());
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
