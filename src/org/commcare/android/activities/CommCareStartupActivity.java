/**
 * 
 */
package org.commcare.android.activities;

import org.commcare.android.R;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.database.TableBuilder;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.Case;
import org.commcare.android.models.FormRecord;
import org.commcare.android.models.Referral;
import org.commcare.android.models.User;
import org.commcare.android.preferences.CommCarePreferences;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.CommCareUpgrader;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.xml.util.UnfullfilledRequirementsException;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * The CommCareStartupActivity is purely responsible for identifying
 * the state of the application (uninstalled, installed) and performing
 * any necessary setup to get to a place where CommCare can load normally.
 * 
 * If the startup activity identifies that the app is installed properly
 * it should not ever require interaction or be visible to the user. 
 * 
 * @author ctsims
 *
 */
public class CommCareStartupActivity extends Activity {
	
	public static final String DATABASE_STATE = "database_state";
	public static final String RESOURCE_STATE = "resource_state";
	
	int dbState;
	int resourceState;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.first_start_screen);
		
		//First, identify the binary state
		dbState = this.getIntent().getIntExtra(DATABASE_STATE, CommCareApplication.STATE_READY);
		resourceState = this.getIntent().getIntExtra(RESOURCE_STATE, CommCareApplication.STATE_READY);
		
		if(dbState == CommCareApplication.STATE_READY && resourceState == CommCareApplication.STATE_READY) {
	        Intent i = new Intent(getIntent());
	        
	        setResult(RESULT_OK, i);
	        finish();

//			Intent i = new Intent(this, CommCareHomeActivity.class);
//			this.startActivity(i);
		} else {
			Button b = (Button)this.findViewById(R.id.start_install);
			b.setOnClickListener(new OnClickListener() {

				public void onClick(View v) {
					if(dbState == CommCareApplication.STATE_READY) {
						//If app is fully initialized, don't need to do anything
					} else if(dbState == CommCareApplication.STATE_UPGRADE) {
						//Upgrayedd
						upgradeDatabase();
					} else if(dbState == CommCareApplication.STATE_UNINSTALLED) {
						//need to install from scratch
						createDataBase();
					}
					
					//Now check on the resources
					if(resourceState == CommCareApplication.STATE_READY) {
						//nothing to do, don't sweat it.
					} else if(resourceState == CommCareApplication.STATE_UNINSTALLED) {
						installResources();
					} else if(resourceState == CommCareApplication.STATE_UPGRADE) {
						//We don't actually see this yet.
					}
					
					//Good to go
			        Intent i = new Intent(getIntent());
			        
			        setResult(RESULT_OK, i);
			        
			        finish();
				}
				
			});

		}
	}
	
	private void installResources() {
		try {
			//This is replicated in the application in a few palces.
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
    		String profile = settings.getString("default_app_server", this.getString(R.string.default_app_server));
    		
    		AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
    		ResourceTable global = platform.getGlobalResourceTable();
    		
    		platform.init(profile, global, false);
		} catch (UnfullfilledRequirementsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private void upgradeDatabase() {
		CommCareUpgrader upgrader = new CommCareUpgrader(this);
		
		SQLiteDatabase database = SQLiteDatabase.openDatabase(GlobalConstants.DB_LOCATION, null, SQLiteDatabase.OPEN_READWRITE);
		int oldVersion = database.getVersion();
		int currentVersion = CommCareApplication._().versionCode();
		
		//Evaluate success here somehow. Also, we'll need to log in to
		//mess with anything in the DB, or any old encrypted files, we need a hook for that...
		upgrader.doUpgrade(database, oldVersion, currentVersion);
	}
	
	private boolean createDataBase() {
		SQLiteDatabase database = SQLiteDatabase.openOrCreateDatabase(GlobalConstants.DB_LOCATION, null);
		try{
			
			database.beginTransaction();
			database.setVersion(CommCareApplication._().versionCode());
			
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
			
			database.setTransactionSuccessful();
		} finally {
			database.endTransaction();
		}
		database.close();
		return true;
	}
}
