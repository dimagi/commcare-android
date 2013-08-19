	
/**
 * 
 */
package org.commcare.dalvik.activities;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.framework.WrappingSpinnerAdapter;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ResourceEngineListener;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
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
@ManagedUi(R.layout.first_start_screen)
public class CommCareSetupActivity extends CommCareActivity<CommCareSetupActivity> implements ResourceEngineListener{
	
//	public static final String DATABASE_STATE = "database_state";
	public static final String RESOURCE_STATE = "resource_state";
	public static final String KEY_PROFILE_REF = "app_profile_ref";
	public static final String KEY_UPGRADE_MODE = "app_upgrade_mode";
	public static final String KEY_ERROR_MODE = "app_error_mode";
	public static final String KEY_REQUIRE_REFRESH = "require_referesh";
	public static final String KEY_AUTO = "is_auto_update";
	public static final String KEY_MAIN_MESSAGE = "main_message_text";
	public static final String KEY_RETRY_BUTTON = "retry_button_text";
	public static final String KEY_RESTART_BUTTON = "restart_button_text";	
	
	public enum UiState { advanced, basic, ready, error, upgrade };
	public UiState uiState = UiState.basic;
	
	public static final int MODE_BASIC = Menu.FIRST;
	public static final int MODE_ADVANCED = Menu.FIRST + 1;
	
	public static final int DIALOG_INSTALL_PROGRESS = 0;
	
	public static final int BARCODE_CAPTURE = 1;
	public static final int MISSING_MEDIA_ACTIVITY=2;
	
	public static final int RETRY_LIMIT = 20;
	
	boolean startAllowed = true;
	
	int dbState;
	int resourceState;
	int retryCount=0;
	
	
	public String incomingRef;
	public boolean canRetry;
	public String displayMessage;
	
	
	View advancedView;
	@UiElement(R.id.screen_first_start_bottom)
	View buttonView;
	EditText editProfileRef;
	TextView mainMessage;
	Spinner urlSpinner;
	Button installButton;
	Button mScanBarcodeButton;
	Button addressEntryButton;
	Button startOverButton;
	Button retryButton;
    
    String [] urlVals;
    int previousUrlPosition=0;
	 
	boolean partialMode = false;
	
	CommCareApp ccApp;
	
	//Whether this needs to be interactive (if it's automatic, we want to skip a lot of the UI stuff
	boolean isAuto = false;
	
	View banner;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		CommCareSetupActivity oldActivity = (CommCareSetupActivity)this.getDestroyedActivityState();
		
		//Grab Views
		editProfileRef = (EditText)this.findViewById(R.id.edit_profile_location);
		advancedView = this.findViewById(R.id.advanced_panel);
		mainMessage = (TextView)this.findViewById(R.id.str_setup_message);
		urlSpinner = (Spinner)this.findViewById(R.id.url_spinner);
		installButton = (Button)this.findViewById(R.id.start_install);
    	mScanBarcodeButton = (Button)this.findViewById(R.id.btn_fetch_uri);
    	addressEntryButton = (Button)this.findViewById(R.id.enter_app_location);
    	startOverButton = (Button)this.findViewById(R.id.start_over);
    	retryButton = (Button)this.findViewById(R.id.retry_install);
    	banner = this.findViewById(R.id.screen_first_start_banner);

    	boolean errorMode;
    	boolean upgradeMode;

    	//Retrieve instance state
		if(savedInstanceState == null) {
			incomingRef = this.getIntent().getStringExtra(KEY_PROFILE_REF);
			upgradeMode = this.getIntent().getBooleanExtra(KEY_UPGRADE_MODE, false);
			errorMode = this.getIntent().getBooleanExtra(KEY_ERROR_MODE, false);
			isAuto = this.getIntent().getBooleanExtra(KEY_AUTO, false);
		} else {
			String uiStateEncoded = savedInstanceState.getString("advanced");
			this.uiState = uiStateEncoded == null ? UiState.basic : UiState.valueOf(UiState.class, uiStateEncoded);
	        incomingRef = savedInstanceState.getString("profileref");
	        upgradeMode = savedInstanceState.getBoolean(KEY_UPGRADE_MODE);
	        isAuto = savedInstanceState.getBoolean(KEY_AUTO);
	        errorMode = savedInstanceState.getBoolean(KEY_ERROR_MODE);
	        mainMessage.setText(savedInstanceState.getString(KEY_MAIN_MESSAGE));
	        retryButton.setText(savedInstanceState.getString(KEY_RETRY_BUTTON));
	        startOverButton.setText(savedInstanceState.getString(KEY_RESTART_BUTTON));
	        
	        //Uggggh, this might not be 100% legit depending on timing, what if we've already reconnected and shut down the dialog?
	        startAllowed = savedInstanceState.getBoolean("startAllowed");
		}
		
		if(upgradeMode){
			this.uiState = uiState.upgrade;
		}
		else if(errorMode){
			this.uiState = uiState.error;
		}
		
		editProfileRef.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
		urlSpinner.setAdapter(new WrappingSpinnerAdapter(urlSpinner.getAdapter(), getResources().getStringArray(R.array.url_list_selected_display)));
		urlVals = getResources().getStringArray(R.array.url_vals);
    	
		urlSpinner.setOnItemSelectedListener(new OnItemSelectedListener(){

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1,
					int arg2, long arg3) {
				if((previousUrlPosition == 0 || previousUrlPosition == 1) && arg2 == 2){
					editProfileRef.setText(R.string.default_app_server);
				}
				else if(previousUrlPosition == 2 && (arg2 == 0 || arg2 == 1)){
					editProfileRef.setText("");
				}
				previousUrlPosition = arg2;
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {}
			
		});
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
			if(dbState == CommCareApplication.STATE_READY && resourceState == CommCareApplication.STATE_READY && this.uiState != UiState.upgrade && this.uiState != uiState.error) {
		        Intent i = new Intent(getIntent());	
		        setResult(RESULT_OK, i);
		        finish();
		        return;
			}
		}
		    	
		mScanBarcodeButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
                try {	
                    Intent i = new Intent("com.google.zxing.client.android.SCAN");
                	//Barcode only
                    i.putExtra("SCAN_FORMATS","QR_CODE");
                    CommCareSetupActivity.this.startActivityForResult(i, BARCODE_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(CommCareSetupActivity.this,"No barcode scanner installed on phone!", Toast.LENGTH_SHORT).show();
                    mScanBarcodeButton.setVisibility(View.GONE);
                }

			}
			
		});
		
		addressEntryButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				setModeToAdvanced();
			}
			
		});
		addressEntryButton.setText(Localization.get("install.button.enter"));
		
		startOverButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(uiState == UiState.upgrade) {
					startResourceInstall(true);
				} else {
					retryCount = 0;
					partialMode = false;
					uiState = uiState.basic;
					refreshView();
				}
			}
			
		});
		
		retryButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(uiState == UiState.upgrade) {
					partialMode = true;
				}
				startResourceInstall(false);
			}
		});
		
		installButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {	
				//Now check on the resources
				if(resourceState == CommCareApplication.STATE_READY) {
					if(uiState != UiState.upgrade) {
						fail(NotificationMessageFactory.message(ResourceEngineOutcomes.StatusFailState), true);
					}
				} else if(resourceState == CommCareApplication.STATE_UNINSTALLED || 
						(resourceState == CommCareApplication.STATE_UPGRADE && uiState == UiState.upgrade)) {
					startResourceInstall();
				}
			}
		});
		
        final View activityRootView = findViewById(R.id.screen_first_start_main);
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
            	int hideAll = CommCareSetupActivity.this.getResources().getInteger(R.integer.login_screen_hide_all_cuttoff);
            	int hideBanner = CommCareSetupActivity.this.getResources().getInteger(R.integer.login_screen_hide_banner_cuttoff);
                int height = activityRootView.getHeight();
                
                if(height < hideAll) {
                	banner.setVisibility(View.GONE);
                } else if(height < hideBanner) {
                	banner.setVisibility(View.GONE);
                }  else {
                	banner.setVisibility(View.VISIBLE);
                }
             }
        });
        
        // reclaim ccApp for resuming installation
        if(oldActivity != null) {
        	this.ccApp = oldActivity.ccApp;
        }
        
        refreshView();
        
        //prevent the keyboard from popping up on entry by refocusing on the main layout
        findViewById(R.id.mainLayout).requestFocus();
        
	}
	
	public void refreshView(){
		switch(uiState){
			case basic:
				this.setModeToBasic();
				break;
			case advanced:
				this.setModeToAdvanced();
				break;
			case error:
				this.setModeToError(true);
				break;
			case upgrade:
				this.setModeToAutoUpgrade();
				break;
			case ready:
				this.setModeToReady(incomingRef);
				break;
		}
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see android.support.v4.app.FragmentActivity#onStart()
	 */
	@Override
	protected void onStart() {
		super.onStart();
		//Moved here to properly attach fragments and such.
		//NOTE: May need to do so elsewhere as well
		if(uiState == UiState.upgrade) {
			refreshView();
			mainMessage.setText(Localization.get("updates.check"));
			startResourceInstall();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.commcare.android.framework.CommCareActivity#getWakeLockingLevel()
	 */
	@Override
	protected int getWakeLockingLevel() {
		return PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE;
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
        outState.putString("profileref", incomingRef);
        outState.putBoolean(KEY_AUTO, isAuto);
        outState.putBoolean("startAllowed", startAllowed);
        outState.putString(KEY_MAIN_MESSAGE, mainMessage.getText().toString());
        outState.putString(KEY_RETRY_BUTTON, retryButton.getText().toString());
        outState.putString(KEY_RESTART_BUTTON, startOverButton.getText().toString());
        
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
				try{
					ReferenceManager._().DeriveReference(incomingRef);
				}
				catch(InvalidReferenceException ire){
					this.setModeToBasic(Localization.get("install.bad.ref"));
					return;
				}
				uiState = uiState.ready;
				this.refreshView();
			}
		}
	}
	
	private String getRef(){
		String ref;
		if(this.uiState == UiState.advanced) {
			int selectedIndex = urlSpinner.getSelectedItemPosition();
			String selectedString = urlVals[selectedIndex];
			if(previousUrlPosition != 2){
				ref = selectedString + editProfileRef.getText().toString();
			}
			else{
				ref = editProfileRef.getText().toString();
			}
		}
		else{
			ref = incomingRef;
		}
		return ref;
	}
	
	private CommCareApp getCommCareApp(){
		CommCareApp app = null;
		
		// we are in upgrade mode, just send back current app
		
		if(uiState == UiState.upgrade){
			app = CommCareApplication._().getCurrentApp();
			return app;
		}
		
		//we have a clean slate, create a new app
		if(partialMode){
			return ccApp;
		}
		else{
			ApplicationRecord newRecord = new ApplicationRecord(PropertyUtils.genUUID().replace("-",""), ApplicationRecord.STATUS_UNINITIALIZED);
			app = new CommCareApp(newRecord);
			return app;
		}
	}

	private void startResourceInstall() {
		this.startResourceInstall(true);
	}
	
	
	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#startBlockingForTask()
	 */
	@Override
	public void startBlockingForTask(int id) {
		super.startBlockingForTask(id);
		this.startAllowed = false;
	}

	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#stopBlockingForTask()
	 */
	@Override
	public void stopBlockingForTask(int id) {
		super.stopBlockingForTask(id);
		this.startAllowed = true;
	}
	
	private void startResourceInstall(boolean startOverUpgrade) {

		if(startAllowed) {
			String ref = getRef();
			
			CommCareApp app = getCommCareApp();
			
			ccApp = app;
			
			ResourceEngineTask<CommCareSetupActivity> task = new ResourceEngineTask<CommCareSetupActivity>(this, uiState == UiState.upgrade, partialMode, app, startOverUpgrade,DIALOG_INSTALL_PROGRESS) {

				@Override
				protected void deliverResult(CommCareSetupActivity receiver, org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes result) {
					if(result == ResourceEngineOutcomes.StatusInstalled){
						receiver.reportSuccess(true);
					} else if(result == ResourceEngineOutcomes.StatusUpToDate){
						receiver.reportSuccess(false);
					} else if(result == ResourceEngineOutcomes.StatusMissing || result == ResourceEngineOutcomes.StatusMissingDetails){
						receiver.failMissingResource(this.missingResourceException, result);
					} else if(result == ResourceEngineOutcomes.StatusBadReqs){
						receiver.failBadReqs(badReqCode, vRequired, vAvailable, majorIsProblem);
					} else if(result == ResourceEngineOutcomes.StatusFailState){
						receiver.failWithNotification(ResourceEngineOutcomes.StatusFailState);
					} else if(result == ResourceEngineOutcomes.StatusNoLocalStorage) {
						receiver.failWithNotification(ResourceEngineOutcomes.StatusNoLocalStorage);
					} else if(result == ResourceEngineOutcomes.StatusBadCertificate){
						receiver.failWithNotification(ResourceEngineOutcomes.StatusBadCertificate);
					} else {
						receiver.failUnknown(ResourceEngineOutcomes.StatusFailUnknown);
					}
				}

				@Override
				protected void deliverUpdate(CommCareSetupActivity receiver, int[]... update) {
					receiver.updateProgress(update[0][0], update[0][1], update[0][2]);
				}

				@Override
				protected void deliverError(CommCareSetupActivity receiver, Exception e) {
					receiver.failUnknown(ResourceEngineOutcomes.StatusFailUnknown);
				}

			};
			
			task.connect(this);
			
			task.execute(ref);
		} else {
			Log.i("commcare-install", "Blocked a resource install press since a task was already running");
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
        } else if(uiState == UiState.ready){
        	basic.setVisible(true);
        	advanced.setVisible(true);
        } else if(uiState == UiState.basic){
        	basic.setVisible(false);
        	advanced.setVisible(true);
        } else{
        	basic.setVisible(false);
        	basic.setVisible(false);
        }
        return true;
    }
    
    public void setModeToAutoUpgrade(){
    	retryButton.setText(Localization.get("upgrade.button.retry"));
    	startOverButton.setText(Localization.get("upgrade.button.startover"));
    	buttonView.setVisibility(View.INVISIBLE);
    }
    
    public void setModeToReady(String incomingRef) {
    	buttonView.setVisibility(View.VISIBLE);
    	mainMessage.setText(Localization.get("install.ready"));
		editProfileRef.setText(incomingRef);
    	advancedView.setVisibility(View.GONE);
    	mScanBarcodeButton.setVisibility(View.GONE);
    	installButton.setVisibility(View.VISIBLE);
    	startOverButton.setText(Localization.get("install.button.startover"));
    	startOverButton.setVisibility(View.VISIBLE);
    	addressEntryButton.setVisibility(View.GONE);
    	retryButton.setVisibility(View.GONE);
    }
    
    public void setModeToError(boolean canRetry){
    	uiState = UiState.error;
    	buttonView.setVisibility(View.VISIBLE);
    	advancedView.setVisibility(View.GONE);
    	mScanBarcodeButton.setVisibility(View.GONE);
    	installButton.setVisibility(View.GONE);
    	startOverButton.setVisibility(View.VISIBLE);
    	addressEntryButton.setVisibility(View.GONE);
    	if(canRetry){
    		retryButton.setVisibility(View.VISIBLE);
    	}
    	else{
    		retryButton.setVisibility(View.GONE);
    	}
    }
    
    public void setModeToBasic(){
    	this.setModeToBasic(Localization.get("install.barcode"));
    }
    
    public void setModeToBasic(String message){
    	buttonView.setVisibility(View.VISIBLE);
    	editProfileRef.setText("");	
    	this.incomingRef = null;
    	mainMessage.setText(message);
    	addressEntryButton.setVisibility(View.VISIBLE);
    	advancedView.setVisibility(View.GONE);
    	mScanBarcodeButton.setVisibility(View.VISIBLE);
    	startOverButton.setVisibility(View.GONE);
    	installButton.setVisibility(View.GONE);
    	retryButton.setVisibility(View.GONE);
    	retryButton.setText(Localization.get("install.button.retry"));
    	startOverButton.setText(Localization.get("install.button.startover"));
    }

    public void setModeToAdvanced(){
    	if(this.uiState == uiState.ready){
    		previousUrlPosition = -1;
    		urlSpinner.setSelection(2);
    	}
    	buttonView.setVisibility(View.VISIBLE);
    	mainMessage.setText(Localization.get("install.manual"));
    	advancedView.setVisibility(View.VISIBLE);
    	mScanBarcodeButton.setVisibility(View.GONE);
    	addressEntryButton.setVisibility(View.GONE);
        installButton.setVisibility(View.VISIBLE);
        startOverButton.setText(Localization.get("install.button.startover"));
        startOverButton.setVisibility(View.VISIBLE);
    	installButton.setEnabled(true);
    	retryButton.setVisibility(View.GONE);
    	retryButton.setText(Localization.get("install.button.retry"));
    	startOverButton.setText(Localization.get("install.button.startover"));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if(uiState != UiState.upgrade) {
	        switch (item.getItemId()) {
	            case MODE_BASIC:
	            	uiState = uiState.basic;
	            	break;
	            case MODE_ADVANCED:
	            	uiState = uiState.advanced;
	            	break;
	        }
	        
	        refreshView();
	        return true;
    	}
        return super.onOptionsItemSelected(item);
    }

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == DIALOG_INSTALL_PROGRESS) {
			ProgressDialog mProgressDialog = new ProgressDialog(this) {
				/* (non-Javadoc)
				 * @see android.app.Dialog#onWindowFocusChanged(boolean)
				 */
				@Override
				public void onWindowFocusChanged(boolean hasFocus) {
					super.onWindowFocusChanged(hasFocus);
					//TODO: Should we generalize this in some way? Not sure if it's happening elsewhere.
					if(hasFocus && Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
						try {
							getWindow().getDecorView().invalidate();
							getWindow().getDecorView().requestLayout();
						} catch(Exception e){
							e.printStackTrace();
							Log.i("CommCare", "Error came up while forcing re-layout for dialog box");
						}
					}
				}
			};
            if(uiState == UiState.upgrade) {
            	mProgressDialog.setTitle(Localization.get("updates.title"));
            	mProgressDialog.setMessage(Localization.get("updates.checking"));
            	refreshView();
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
        if(uiState == UiState.upgrade) {
        	if(phase == ResourceEngineTask.PHASE_DOWNLOAD) {
        		updateProgress(DIALOG_INSTALL_PROGRESS, Localization.get("updates.found", new String[] {""+done,""+total}));
        	} if(phase == ResourceEngineTask.PHASE_COMMIT) {
        		updateProgress(DIALOG_INSTALL_PROGRESS,Localization.get("updates.downloaded"));
        	}
        }
		else {
			updateProgress(DIALOG_INSTALL_PROGRESS, Localization.get("profile.found", new String[]{""+done,""+total}));
		}
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

	public void fail(NotificationMessage message) {
		fail(message, false);
	}
	
	public void fail(NotificationMessage message, boolean alwaysNotify) {	
		fail(message, alwaysNotify, true);
	}
	
	public void fail(NotificationMessage message, boolean alwaysNotify, boolean canRetry){
		Toast.makeText(this, message.getTitle(), Toast.LENGTH_LONG).show();
		
		uiState = UiState.error;
		
		retryCount++;
		
		if(retryCount > RETRY_LIMIT){
			canRetry = false;
		}
		
		if(isAuto || alwaysNotify) {
			CommCareApplication._().reportNotificationMessage(message);
		}
		if(isAuto) {
			done(false);
		} else {
			if(alwaysNotify) {
				this.displayMessage= Localization.get("notification.for.details.wrapper", new String[] {message.getDetails()});
				this.canRetry = canRetry;
				
				refreshView();
			} else {
				this.displayMessage= message.getDetails();
				this.canRetry = canRetry;
				refreshView();
				
				String fullErrorMessage = message.getDetails();
				
				if(alwaysNotify){
					fullErrorMessage = fullErrorMessage + message.getAction();
				}
				
				mainMessage.setText(fullErrorMessage);
			}
		}
		
		refreshView();
	}
	
	public void setUiState(UiState uis){
		this.uiState = uis;
	}

	// All final paths from the Update are handled here (Important! Some interaction modes should always auto-exit this activity)
	// Everything here should call one of: fail() or done() 
	
	public void reportSuccess(boolean appChanged) {
		//If things worked, go ahead and clear out any warnings to the contrary
		CommCareApplication._().clearNotifications("install_update");
		
		if(!appChanged) {
			Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
		}
		done(appChanged);
	}

	public void failMissingResource(UnresolvedResourceException ure, ResourceEngineOutcomes statusMissing) {
		fail(NotificationMessageFactory.message(statusMissing, new String[] {null, ure.getResource().getResourceId(), ure.getMessage()}), ure.isMessageUseful());
		
	}

	public void failBadReqs(int code, String vRequired, String vAvailable, boolean majorIsProblem) {
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
		fail(NotificationMessageFactory.message(unknown));
	}

	public void failWithNotification(ResourceEngineOutcomes statusfailstate) {
		fail(NotificationMessageFactory.message(statusfailstate), true);
	}
    
    @Override
    public void onBackPressed(){
        if(uiState == UiState.advanced) {
        	setModeToBasic();
        } else if(uiState == UiState.ready){
        	setModeToBasic();
        } else{
        	super.onBackPressed();
        }
    }
}
