/**
 * 
 */
package org.commcare.android.activities;

import org.commcare.android.R;
import org.commcare.android.application.CommCareApplication;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.xml.util.UnfullfilledRequirementsException;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
public class CommCareSetupActivity extends Activity {
	
	public static final String DATABASE_STATE = "database_state";
	public static final String RESOURCE_STATE = "resource_state";
	public static final String APP_PROFILE_REF = "app_profile_ref";
	
	public static final int MODE_BASIC = Menu.FIRST;
	public static final int MODE_ADVANCED = Menu.FIRST + 1;
	
	int dbState;
	int resourceState;
	
	String profileRef;
	
	View advancedView;
	EditText editProfileRef;
	TextView mainMessage;
	boolean advanced = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.first_start_screen);
		
		advancedView = this.findViewById(R.id.advanced_panel);
		editProfileRef = (EditText)this.findViewById(R.id.edit_profile_location);
		mainMessage = (TextView)this.findViewById(R.id.str_setup_message);
		
		//First, identify the binary state
		dbState = this.getIntent().getIntExtra(DATABASE_STATE, CommCareApplication.STATE_READY);
		resourceState = this.getIntent().getIntExtra(RESOURCE_STATE, CommCareApplication.STATE_READY);
		profileRef = this.getIntent().getStringExtra(APP_PROFILE_REF);
		if(profileRef == null) {
		    profileRef = PreferenceManager.getDefaultSharedPreferences(this).getString("default_app_server", this.getString(R.string.default_app_server));
		}
		
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
					} 
					
					//Now check on the resources
					if(resourceState == CommCareApplication.STATE_READY) {
						//nothing to do, don't sweat it.
					} else if(resourceState == CommCareApplication.STATE_UNINSTALLED) {
						if(!installResources()) {
							return;
						}
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
	
	private boolean installResources() {
		
		String ref = profileRef;
		if(advanced) {
			ref = editProfileRef.getText().toString();
		}
		AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
		
		try {
			//This is replicated in the application in a few places.
    		ResourceTable global = platform.getGlobalResourceTable();
    		
    		platform.init(ref, global, false);
    		return true;
		} catch (UnfullfilledRequirementsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnresolvedResourceException e) {
			Toast.makeText(this, "Uh oh! There was a problem with the initialization...", Toast.LENGTH_LONG).show();
			
			String error = "A serious problem occured! Couldn't find the resource with id: " + e.getResource().getResourceId() + ". Check the profile url in the advanced mode and make sure you have a network connection.";
			mainMessage.setText(error);
			
			//TODO: Advanced mode button to view exception and possibly send it.
			
			ResourceTable global = platform.getGlobalResourceTable();
			
			//Install was botched, clear anything left lying around....
			global.clear();
		}
		return false;
	}
	
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if(advanced) {
        	menu.removeItem(MODE_ADVANCED);
	        menu.add(0, MODE_BASIC, 0, "Basic Mode").setIcon(
	                android.R.drawable.ic_menu_help);
        } else {
            menu.removeItem(MODE_BASIC);
        	menu.add(0, MODE_ADVANCED, 0, "Advanced Mode").setIcon(
	                android.R.drawable.ic_menu_edit);
        }
	    return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MODE_BASIC:
            	advanced = false;
            	advancedView.setVisibility(View.INVISIBLE);
                return true;
            case MODE_ADVANCED:
            	advanced = true;
            	editProfileRef.setText(profileRef);
            	advancedView.setVisibility(View.VISIBLE);
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
