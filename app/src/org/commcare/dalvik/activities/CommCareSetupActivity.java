/**
 * 
 */
package org.commcare.dalvik.activities;

import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ResourceEngineListener;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.Resource;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
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
public class CommCareSetupActivity extends Activity implements ResourceEngineListener {
	
//	public static final String DATABASE_STATE = "database_state";
	public static final String RESOURCE_STATE = "resource_state";
	public static final String KEY_PROFILE_REF = "app_profile_ref";
	public static final String KEY_UPGRADE_MODE = "app_upgrade_mode";
	public static final String KEY_REQUIRE_REFRESH = "require_referesh";
	public static final String KEY_AUTO = "is_auto_update";
	
	public enum UiState { advanced, basic, ready };
	public UiState uiState = UiState.basic;
	
	public static final int MODE_BASIC = Menu.FIRST;
	public static final int MODE_ADVANCED = Menu.FIRST + 1;
	
	public static final int DIALOG_PROGRESS = 0;
	
	public static final int BARCODE_CAPTURE = 1;
	
	int dbState;
	int resourceState;
	
	String incomingRef;
	
	View advancedView;
	EditText editProfileRef;
	TextView mainMessage;
	Button installButton;
	Button mScanBarcodeButton;
    private ProgressDialog mProgressDialog;
	
	boolean upgradeMode = false;
	
	//Whether this needs to be interactive (if it's automatic, we want to skip a lot of the UI stuff
	boolean isAuto = false;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.first_start_screen);
		
		if(savedInstanceState == null) {
			incomingRef = this.getIntent().getStringExtra(KEY_PROFILE_REF);
			upgradeMode = this.getIntent().getBooleanExtra(KEY_UPGRADE_MODE, false);
			isAuto = this.getIntent().getBooleanExtra(KEY_AUTO, false);
		} else {
			String uiStateEncoded = savedInstanceState.getString("advanced");
			this.uiState = uiStateEncoded == null ? UiState.basic : UiState.valueOf(UiState.class, uiStateEncoded);
	        incomingRef = savedInstanceState.getString("profileref");
	        upgradeMode = savedInstanceState.getBoolean(KEY_UPGRADE_MODE);
	        upgradeMode = savedInstanceState.getBoolean(KEY_AUTO);
		}
		
		advancedView = this.findViewById(R.id.advanced_panel);
		mainMessage = (TextView)this.findViewById(R.id.str_setup_message);

		//First, identify the binary state
		dbState = CommCareApplication._().getDatabaseState();
		resourceState = CommCareApplication._().getAppResourceState();
		
		if(Intent.ACTION_VIEW.equals(this.getIntent().getAction())) {
			//We got called from an outside application, it's gonna be a wild ride!
			incomingRef = this.getIntent().getData().toString();
			//Now just start up normally.
		} else {
			//Otherwise we're starting up being called from inside the app. Check to see if everything is set
			//and we can just skip this unless it's upgradeMode
			if(dbState == CommCareApplication.STATE_READY && resourceState == CommCareApplication.STATE_READY && !upgradeMode) {
		        Intent i = new Intent(getIntent());
		        
		        setResult(RESULT_OK, i);
		        finish();
		        return;
			}
		}
		
		
		editProfileRef = (EditText)this.findViewById(R.id.edit_profile_location);
		installButton = (Button)this.findViewById(R.id.start_install);
    	mScanBarcodeButton = (Button)this.findViewById(R.id.btn_fetch_uri);
    	
		mScanBarcodeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
                try {
                    Intent i = new Intent("com.google.zxing.client.android.SCAN");
                    CommCareSetupActivity.this.startActivityForResult(i, BARCODE_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(CommCareSetupActivity.this,"No barcode scanner installed on phone!", Toast.LENGTH_SHORT).show();
                    mScanBarcodeButton.setVisibility(View.GONE);
                }

			}
			
		});
		
		if(incomingRef == null) {
			editProfileRef.setText(PreferenceManager.getDefaultSharedPreferences(this).getString("default_app_server", this.getString(R.string.default_app_server)));
			
			if(this.uiState == UiState.advanced) {
				this.setModeToAdvanced();
			} else {
				this.setModeToBasic();
			}
		} else {
			this.setModeToReady(incomingRef);
		}
		
		installButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {	
				
				if(dbState == CommCareApplication.STATE_READY) {
					//If app is fully initialized, don't need to do anything
				} 
				
				//Now check on the resources
				if(resourceState == CommCareApplication.STATE_READY) {
					if(!upgradeMode) {
						fail(NotificationMessageFactory.message(ResourceEngineOutcomes.StatusFailState), true);
					}
				} else if(resourceState == CommCareApplication.STATE_UNINSTALLED) {
					startResourceInstall();
				} else if(resourceState == CommCareApplication.STATE_UPGRADE && upgradeMode) {
					//This will come up if the upgrade has problems  
					startResourceInstall();
				}
			}
		});
		if(upgradeMode) {
			mainMessage.setText(Localization.get("updates.check"));
			startResourceInstall();
		}
	}
	
    /*
     * (non-Javadoc)
     * 
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("advanced", uiState.toString());
        outState.putString("profileref", uiState == UiState.advanced ? editProfileRef.getText().toString() : incomingRef);
        outState.putBoolean(KEY_UPGRADE_MODE, upgradeMode);
        outState.putBoolean(KEY_AUTO, isAuto);
    }
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == BARCODE_CAPTURE) {
			if(resultCode == Activity.RESULT_CANCELED) {
				//Basically nothing
			} else if(resultCode == Activity.RESULT_OK) {
    			String result = data.getStringExtra("SCAN_RESULT");
				incomingRef = result;
				//Definitely have a URI now.
				this.setModeToReady(result);
			}
		}
	}

	private void startResourceInstall() {
		
		String ref = incomingRef;
		if(this.uiState == UiState.advanced) {
			ref = editProfileRef.getText().toString();
		}
		
		ResourceEngineTask task = new ResourceEngineTask(this, upgradeMode);
		task.setListener(this);
		
		task.execute(ref);
		
		this.showDialog(DIALOG_PROGRESS);
	}

	public void done(boolean requireRefresh) {
		//TODO: We might have gotten here due to being called from the outside, in which
		//case we should manually start up the home activity
		
		if(Intent.ACTION_VIEW.equals(CommCareSetupActivity.this.getIntent().getAction())) {
			//Call out to CommCare Home
 	       Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
 	       i.putExtra(KEY_REQUIRE_REFRESH, requireRefresh);
 	       startActivity(i);
 	       finish();
 	       
 	       return;
		} else {
			//Good to go
	        Intent i = new Intent(getIntent());
	        i.putExtra(KEY_REQUIRE_REFRESH, requireRefresh);
	        setResult(RESULT_OK, i);
	        finish();
	        return;
		}
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
    	menu.add(0, MODE_BASIC, 0, Localization.get("menu.basic")).setIcon(android.R.drawable.ic_menu_help);
    	menu.add(0, MODE_ADVANCED, 0, Localization.get("menu.advanced")).setIcon(android.R.drawable.ic_menu_edit);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        
        MenuItem basic = menu.findItem(MODE_BASIC);
        MenuItem advanced = menu.findItem(MODE_ADVANCED);
        
        if(uiState == UiState.advanced) {
        	basic.setVisible(true);
        	advanced.setVisible(false);
        } else {
        	basic.setVisible(false);
        	advanced.setVisible(true);
        }
        return true;
    }
    
    public void setModeToReady(String incomingRef) {
    	this.uiState = UiState.ready;
    	mainMessage.setText(Localization.get("install.ready"));
		editProfileRef.setText(incomingRef);
    	advancedView.setVisibility(View.INVISIBLE);
    	mScanBarcodeButton.setVisibility(View.INVISIBLE);
    	installButton.setVisibility(View.VISIBLE);
    }
    
    public void setModeToBasic(){
    	this.uiState = UiState.basic;
    	this.incomingRef = null;
    	mainMessage.setText(Localization.get("install.barcode"));
    	advancedView.setVisibility(View.INVISIBLE);
    	mScanBarcodeButton.setVisibility(View.VISIBLE);
    	installButton.setVisibility(View.GONE);
    }

    public void setModeToAdvanced(){
    	this.uiState = UiState.advanced;
    	mainMessage.setText(Localization.get("install.manual"));
    	advancedView.setVisibility(View.VISIBLE);
    	mScanBarcodeButton.setVisibility(View.INVISIBLE);
        installButton.setVisibility(View.VISIBLE);
    	installButton.setEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if(!upgradeMode) {
	        switch (item.getItemId()) {
	            case MODE_BASIC:
	            	setModeToBasic();
	                return true;
	            case MODE_ADVANCED:
	            	setModeToAdvanced();
	            	return true;
	        }
    	}
        return super.onOptionsItemSelected(item);
    }
    
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == DIALOG_PROGRESS) {
            mProgressDialog = new ProgressDialog(this);
            if(upgradeMode) {
            	mProgressDialog.setTitle(Localization.get("updates.title"));
            	mProgressDialog.setMessage(Localization.get("updates.checking"));
            } else {
            	mProgressDialog.setTitle(Localization.get("updates.resources.initialization"));
            	mProgressDialog.setMessage(Localization.get("updates.resources.profile"));
            }
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            return mProgressDialog;
		}
		return null;
	}
	

	public void updateProgress(int done, int total, int phase) {
		if(mProgressDialog != null) {
            if(upgradeMode) {

            	if(phase == ResourceEngineTask.PHASE_DOWNLOAD) {
            		mProgressDialog.setMessage(Localization.get("updates.found", new String[] {""+done,""+total}));
            	} if(phase == ResourceEngineTask.PHASE_COMMIT) {
            		mProgressDialog.setMessage(Localization.get("updates.downloaded"));
            	}
            }
			else {
				mProgressDialog.setMessage(Localization.get("profile.found", new String[]{""+done,""+total}));
			}
		}
	}

	public void fail(NotificationMessage message) {
		fail(message, false);
	}
	
	public void fail(NotificationMessage message, boolean alwaysNotify) {
		Toast.makeText(this, message.getTitle(), Toast.LENGTH_LONG).show();
		
		if(isAuto || alwaysNotify) {
			CommCareApplication._().reportNotificationMessage(message);
		}
		if(isAuto) {
			done(false);
		} else {
			if(alwaysNotify) {
				mainMessage.setText(Localization.get("install.error.details", new String[] {message.getDetails()}));
			} else {
				mainMessage.setText(message.getDetails());
			}
		}
	}

	// All final paths from the Update are handled here (Important! Some interaction modes should always auto-exit this activity) 
	
	public void reportSuccess(boolean appChanged) {
		this.dismissDialog(DIALOG_PROGRESS);
		
		//If things worked, go ahead and clear out any warnings to the contrary
		CommCareApplication._().clearNotifications("install_update");
		
		if(!appChanged) {
			Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
		}
		done(appChanged);
	}

	public void failMissingResource(Resource r, ResourceEngineOutcomes statusMissing) {
		this.dismissDialog(DIALOG_PROGRESS);
		fail(NotificationMessageFactory.message(statusMissing, new String[] {null, r.getResourceId(), null}));
		
	}

	public void failBadReqs(int code, String vRequired, String vAvailable, boolean majorIsProblem) {
		this.dismissDialog(DIALOG_PROGRESS);

		String versionMismatch = Localization.get("install.version.mismatch", new String[] {vRequired,vAvailable});
		
		String error = "";
		if(majorIsProblem){
			error=Localization.get("install.major.mismatch");
		}
		else{
			error=Localization.get("install.minor.mismatch");
		}
		
		fail(NotificationMessageFactory.message(ResourceEngineOutcomes.StatusBadReqs, new String[] {null, versionMismatch, error}), true);
	}

	public void failUnknown(ResourceEngineOutcomes unknown) {
		this.dismissDialog(DIALOG_PROGRESS);
		
		fail(NotificationMessageFactory.message(unknown));
	}

	public void failBadState(ResourceEngineOutcomes statusfailstate) {
		this.dismissDialog(DIALOG_PROGRESS);
		fail(NotificationMessageFactory.message(statusfailstate), true);
	}
}
