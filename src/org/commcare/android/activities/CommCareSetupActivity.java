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
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
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
	
	String incomingRef;
	
	View advancedView;
	EditText editProfileRef;
	TextView mainMessage;
	Button installButton;
	boolean advanced = false;
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.first_start_screen);
		
		if(savedInstanceState == null) {
			incomingRef = this.getIntent().getStringExtra(APP_PROFILE_REF);
		} else {
	        advanced = savedInstanceState.getBoolean("advanced");
	        incomingRef = savedInstanceState.getString("profileref");
		}
		
		advancedView = this.findViewById(R.id.advanced_panel);
		mainMessage = (TextView)this.findViewById(R.id.str_setup_message);

		//First, identify the binary state
		dbState = CommCareApplication._().getDatabaseState();
		resourceState = CommCareApplication._().getAppResourceState();
		
    	advancedView.setVisibility(advanced ? View.VISIBLE : View.INVISIBLE);
		
		if(Intent.ACTION_VIEW.equals(this.getIntent().getAction())) {
			//We got called from an outside application, it's gonna be a wild ride!
			incomingRef = this.getIntent().getData().toString();
			
			//Now just start up normally.
		} else {
			//Otherwise we're starting up being called from inside the app. Check to see if everything is set
			//and we can just skip tihs
			if(dbState == CommCareApplication.STATE_READY && resourceState == CommCareApplication.STATE_READY) {
		        Intent i = new Intent(getIntent());
		        
		        setResult(RESULT_OK, i);
		        finish();
		        return;
			}
		}
		
		
		editProfileRef = (EditText)this.findViewById(R.id.edit_profile_location);
		installButton = (Button)this.findViewById(R.id.start_install);
		
		if(incomingRef == null) {
			mainMessage.setText("Welcome to CommCare! To proceed with installation, please navigate to a CommCare Profile on your Web Browser.");
			editProfileRef.setText(PreferenceManager.getDefaultSharedPreferences(this).getString("default_app_server", this.getString(R.string.default_app_server)));
			installButton.setEnabled(false);
		} else {
			editProfileRef.setText(incomingRef);
		}
		
		installButton.setOnClickListener(new OnClickListener() {

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
				
		        //TODO: We might have gotten here due to being called from the outside, in which
				//case we should manually start up the home activity
				
				if(Intent.ACTION_VIEW.equals(CommCareSetupActivity.this.getIntent().getAction())) {
					
					//Need to do the init from here, since cchome won't know we're returning
					CommCareApplication._().initializeGlobalResources();
					
					//Call out to CommCare Home
	     	        Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
	     	        
	     	       CommCareSetupActivity.this.startActivity(i);
	     	       finish();
	     	       return;
				} else {
					//Good to go
			        Intent i = new Intent(getIntent());
			        setResult(RESULT_OK, i);
			        finish();
			        return;
				}
			}
			
		});
	}
	
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("advanced", advanced);
        outState.putString("profileref", advanced ? editProfileRef.getText().toString() : incomingRef);
    }
	
	private boolean installResources() {
		
		String ref = incomingRef;
		if(advanced) {
			ref = editProfileRef.getText().toString();
		}
		AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
		
		try {
			//This is replicated in the application in a few places.
    		ResourceTable global = platform.getGlobalResourceTable();
    		
    		platform.init(ref, global, false);
    		
    		//Alll goood, we should make this the current profile ref.
    		//TODO: _OR_ we should use the one in the profile...
    		
    		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    		Editor edit = prefs.edit();
    		edit.putString("default_app_server", ref);
    		edit.commit();
    		
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
	        //menu.add(0, MODE_BASIC, 0, "Basic Mode").setIcon(
	                //android.R.drawable.ic_menu_help);
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
            	advancedView.setVisibility(View.VISIBLE);
            	installButton.setEnabled(true);
            	return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
