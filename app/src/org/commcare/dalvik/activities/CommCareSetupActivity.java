package org.commcare.dalvik.activities;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.framework.WrappingSpinnerAdapter;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ResourceEngineListener;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.android.util.MarkupUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.EditText;
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
    
//    public static final String DATABASE_STATE = "database_state";
    public static final String RESOURCE_STATE = "resource_state";
    public static final String KEY_PROFILE_REF = "app_profile_ref";
    public static final String KEY_UPGRADE_MODE = "app_upgrade_mode";
    public static final String KEY_ERROR_MODE = "app_error_mode";
    public static final String KEY_REQUIRE_REFRESH = "require_referesh";
    public static final String KEY_AUTO = "is_auto_update";
    public static final String KEY_START_OVER = "start_over_uprgrade";
    public static final String KEY_LAST_INSTALL = "last_install_time";
    
    /*
     * enum indicating which UI mconfiguration should be shown.
     * basic: First install, user can scan barcode and move to ready mode or select advanced mode
     * advanced: First install, user can enter bit.ly or URL directly, or return to basic mode
     * ready: First install, barcode has been scanned. Can move to advanced mode to inspect URL, or proceed to install
     * upgrade: App installed already. Buttons aren't shown, trying to update app with no user input
     * error: Installation or Upgrade has failed, offer to retry or restart. upgrade/install differentiated with inUpgradeMode boolean
     */
    
    public enum UiState { advanced, basic, ready, error, upgrade};
    public UiState uiState = UiState.basic;
    
    public static final int MODE_BASIC = Menu.FIRST;
    public static final int MODE_ADVANCED = Menu.FIRST + 1;
    public static final int MODE_ARCHIVE = Menu.FIRST + 2;
    
    public static final int BARCODE_CAPTURE = 1;
    public static final int MISSING_MEDIA_ACTIVITY=2;
    public static final int ARCHIVE_INSTALL = 3;
    public static final int DIALOG_INSTALL_PROGRESS = 4; 

    
    public static final int RETRY_LIMIT = 20;
    
    boolean startAllowed = true;
    boolean inUpgradeMode = false;
    
    int dbState;
    int resourceState;
    int retryCount=0;
    
    
    public String incomingRef;
    public boolean canRetry;
    public String displayMessage;
    
    @UiElement(R.id.advanced_panel)
    View advancedView;
    @UiElement(R.id.screen_first_start_bottom)
    View buttonView;
    @UiElement(R.id.edit_profile_location)
    EditText editProfileRef;
    @UiElement(R.id.str_setup_message)
    TextView mainMessage;
    @UiElement(R.id.url_spinner)
    Spinner urlSpinner;
    @UiElement(R.id.start_install)
    Button installButton;
    @UiElement(R.id.btn_fetch_uri)
    Button mScanBarcodeButton;
    @UiElement(R.id.enter_app_location)
    Button addressEntryButton;
    @UiElement(R.id.start_over)
    Button startOverButton;
    @UiElement(R.id.view_notification)
    Button viewNotificationButton;
    @UiElement(R.id.retry_install)
    Button retryButton;
    @UiElement(R.id.screen_first_start_banner)
    View banner;
    
    String [] urlVals;
    int previousUrlPosition=0;
     
    boolean partialMode = false;
    
    CommCareApp ccApp;
    
    //Whether this needs to be interactive (if it's automatic, we want to skip a lot of the UI stuff
    boolean isAuto = false;
    
    /* used to keep track of whether or not the previous resource table was in a 
     * 'fresh' (empty or installed) state before the last install ran
     */
    boolean resourceTableWasFresh;
    static final long START_OVER_THRESHOLD = 604800000; //1 week in milliseconds
    
    private BroadcastReceiver purgeNotificationReceiver = null;
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        CommCareSetupActivity oldActivity = (CommCareSetupActivity)this.getDestroyedActivityState();

        //Retrieve instance state
        if(savedInstanceState == null) {
            if(Intent.ACTION_VIEW.equals(this.getIntent().getAction())) {
                
                //We got called from an outside application, it's gonna be a wild ride!
                incomingRef = this.getIntent().getData().toString();
                
                if(incomingRef.contains(".ccz")){
                    // make sure this is in the file system
                    boolean isFile = incomingRef.contains("file://");
                    if(isFile){
                        // remove file:// prepend
                        incomingRef = incomingRef.substring(incomingRef.indexOf("//")+2);
                        Intent i = new Intent(this, InstallArchiveActivity.class);
                        i.putExtra(InstallArchiveActivity.ARCHIVE_REFERENCE, incomingRef);
                        startActivityForResult(i, ARCHIVE_INSTALL);
                    }
                    else{
                        // currently down allow other locations like http://
                        fail(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Bad_Archive_File), true, false);
                    }
                }
                else{
                    this.uiState=uiState.ready;
                    //Now just start up normally.
                }
            } else{
                
                incomingRef = this.getIntent().getStringExtra(KEY_PROFILE_REF);
                
            }
            inUpgradeMode = this.getIntent().getBooleanExtra(KEY_UPGRADE_MODE, false);
            isAuto = this.getIntent().getBooleanExtra(KEY_AUTO, false);
        } else {
            String uiStateEncoded = savedInstanceState.getString("advanced");
            this.uiState = uiStateEncoded == null ? UiState.basic : UiState.valueOf(UiState.class, uiStateEncoded);
            incomingRef = savedInstanceState.getString("profileref");
            inUpgradeMode = savedInstanceState.getBoolean(KEY_UPGRADE_MODE);
            isAuto = savedInstanceState.getBoolean(KEY_AUTO);
            //Uggggh, this might not be 100% legit depending on timing, what if we've already reconnected and shut down the dialog?
            startAllowed = savedInstanceState.getBoolean("startAllowed");
        }
        
        // if we are in upgrade mode we want the UiState to reflect that, unless we are showing an error
        if(inUpgradeMode && this.uiState != UiState.error){
            this.uiState = UiState.upgrade;
        }
        
        editProfileRef.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        urlSpinner.setAdapter(new WrappingSpinnerAdapter(urlSpinner.getAdapter(), getResources().getStringArray(R.array.url_list_selected_display)));
        urlVals = getResources().getStringArray(R.array.url_vals);
        
        urlSpinner.setOnItemSelectedListener(new OnItemSelectedListener(){

            /*
             * (non-Javadoc)
             * @see android.widget.AdapterView.OnItemSelectedListener#onItemSelected(android.widget.AdapterView, android.view.View, int, long)
             */
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

            /*
             * (non-Javadoc)
             * @see android.widget.AdapterView.OnItemSelectedListener#onNothingSelected(android.widget.AdapterView)
             */
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {}
            
        });
        //First, identify the binary state
        dbState = CommCareApplication._().getDatabaseState();
        resourceState = CommCareApplication._().getAppResourceState();
        
        if(!Intent.ACTION_VIEW.equals(this.getIntent().getAction())) {
            //Otherwise we're starting up being called from inside the app. Check to see if everything is set
            //and we can just skip this unless it's upgradeMode
            if(dbState == CommCareApplication.STATE_READY && resourceState == CommCareApplication.STATE_READY && !inUpgradeMode) {
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
        
        mScanBarcodeButton.setText("Scan Barcode");
        
        addressEntryButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                setUiState(UiState.advanced);
                refreshView();
            }
            
        });
        addressEntryButton.setText(MarkupUtil.localizeStyleSpannable("install.button.enter"));
        
        startOverButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if(inUpgradeMode) {
                    startResourceInstall(true);
                } else {
                    retryCount = 0;
                    partialMode = false;
                    setUiState(UiState.basic);
                    refreshView();
                }
            }

        });

        // Hide "See More" button when notification is cleared
        // (by any method: button press, viewing from drawer, or clearing from drawer)
        purgeNotificationReceiver = new BroadcastReceiver() {
            /*
             * (non-Javadoc)
             * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
             */
            @Override
            public void onReceive(Context context, Intent intent) {
                viewNotificationButton.setVisibility(View.GONE);
            }
        };
        registerReceiver(purgeNotificationReceiver, new IntentFilter(CommCareApplication.ACTION_PURGE_NOTIFICATIONS));

        // "See More" launches standard notification-viewing activity
        viewNotificationButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(CommCareSetupActivity.this, MessageActivity.class);
                CommCareSetupActivity.this.startActivity(i);
            }
        });
        
        retryButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                viewNotificationButton.setVisibility(View.GONE);
                partialMode = true;
                startResourceInstall(false);
            }
        });
        
        installButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {    
                //Now check on the resources
                if(resourceState == CommCareApplication.STATE_READY) {
                    if(!inUpgradeMode || uiState != UiState.error) {
                        fail(NotificationMessageFactory.message(ResourceEngineOutcomes.StatusFailState), true);
                    }
                } else if(resourceState == CommCareApplication.STATE_UNINSTALLED || 
                        (resourceState == CommCareApplication.STATE_UPGRADE && inUpgradeMode)) {
                    startResourceInstall();
                }
            }
        });
        
        
        final View activityRootView = findViewById(R.id.screen_first_start_main);
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            /*
             * (non-Javadoc)
             * @see android.view.ViewTreeObserver.OnGlobalLayoutListener#onGlobalLayout()
             */
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
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onDestroy()
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (purgeNotificationReceiver != null) {
            unregisterReceiver(purgeNotificationReceiver);
        }
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
            //mainMessage.setText(Localization.get("updates.check"));
            mainMessage.setText(MarkupUtil.getCustomSpannableKey("yellow-bg",Localization.get("updates.check")));
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
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("advanced", uiState.toString());
        outState.putString("profileref", incomingRef);
        outState.putBoolean(KEY_AUTO, isAuto);
        outState.putBoolean("startAllowed", startAllowed);
        outState.putBoolean(KEY_UPGRADE_MODE, inUpgradeMode);
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
                setUiState(UiState.ready);
                this.refreshView();
            }
        }
        if(requestCode == ARCHIVE_INSTALL){
            if(resultCode == Activity.RESULT_CANCELED) {
                //Basically nothing
            } else if(resultCode == Activity.RESULT_OK) {
                String result = data.getStringExtra(InstallArchiveActivity.ARCHIVE_REFERENCE);
                incomingRef = result;
                //Definitely have a URI now.
                try{
                    ReferenceManager._().DeriveReference(incomingRef);
                }
                catch(InvalidReferenceException ire){
                    this.setModeToBasic(Localization.get("install.bad.ref"));
                    return;
                }
                setUiState(UiState.ready);
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
        
        if(inUpgradeMode){
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
        CommCareApp ccApp = getCommCareApp();
        long lastInstallTime = ccApp.getAppPreferences().getLong(KEY_LAST_INSTALL, -1);
        if (System.currentTimeMillis() - lastInstallTime > START_OVER_THRESHOLD) {
            /*If we are triggering a start over install due to the time threshold
             * when there is a partial resource table that we could be using, send
             * a message to log this.
             */
            ResourceTable temporary = ccApp.getCommCarePlatform().getUpgradeResourceTable();
            if (temporary.getTableReadiness() == ResourceTable.RESOURCE_TABLE_PARTIAL) {
                Logger.log(AndroidLogger.TYPE_RESOURCES, "A start-over on installation has been "
                        + "triggered by the time threshold when there is an existing partial "
                        + "resource table that could be used.");
            }
            startResourceInstall(true);
        }
        else {
            startResourceInstall(ccApp.getAppPreferences().getBoolean(KEY_START_OVER, true));
        }
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
    
    /**
     * @param startOverUpgrade - what determines whether CommCarePlatform.stageUpgradeTable()
     * reuses the last version of the upgrade table, or starts over
     */
    private void startResourceInstall(boolean startOverUpgrade) {
        if(startAllowed) {
            String ref = getRef();
            
            CommCareApp app = getCommCareApp();

            ccApp = app;

            /* store what the state of the resource table was before this install, 
             * so we can compare it to the state after and decide if this should 
             * count as a 'last install time'
             */
            int tableStateBeforeInstall = ccApp.getCommCarePlatform().getUpgradeResourceTable().
                    getTableReadiness();
            this.resourceTableWasFresh = tableStateBeforeInstall == ResourceTable.RESOURCE_TABLE_EMPTY ||
                     tableStateBeforeInstall == ResourceTable.RESOURCE_TABLE_INSTALLED;
            
            CustomProgressDialog lastDialog = getCurrentDialog();
            /* used to tell the ResourceEngineTask whether or not it should sleep before 
             * it starts, set based on whether we are currently in keep trying mode */
            boolean shouldSleep = (lastDialog == null) ? false : lastDialog.isChecked();
            
            ResourceEngineTask<CommCareSetupActivity> task = new ResourceEngineTask<CommCareSetupActivity>(this, 
                    inUpgradeMode, partialMode, app, startOverUpgrade, DIALOG_INSTALL_PROGRESS, shouldSleep) {

                /*
                 * (non-Javadoc)
                 * @see org.commcare.android.tasks.templates.CommCareTask#deliverResult(java.lang.Object, java.lang.Object)
                 */
                @Override
                protected void deliverResult(CommCareSetupActivity receiver, org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes result) {
                    boolean startOverInstall;
                    if(result == ResourceEngineOutcomes.StatusInstalled){
                        startOverInstall = false;
                        receiver.reportSuccess(true);
                    } else if(result == ResourceEngineOutcomes.StatusUpToDate){
                        startOverInstall = false;
                        receiver.reportSuccess(false);
                    } else if (result == ResourceEngineOutcomes.StatusMissing || result == ResourceEngineOutcomes.StatusMissingDetails){
                        startOverInstall = false;
                        CustomProgressDialog lastDialog = receiver.getCurrentDialog();
                        boolean inKeepTryingMode = (lastDialog == null) ? false : lastDialog.isChecked();
                        if (inKeepTryingMode) {
                            receiver.startResourceInstall(false);
                        } else {
                            receiver.failMissingResource(this.missingResourceException, result);
                        }
                    } else if (result == ResourceEngineOutcomes.StatusBadReqs){
                        startOverInstall = false;
                        receiver.failBadReqs(badReqCode, vRequired, vAvailable, majorIsProblem);
                    } else if (result == ResourceEngineOutcomes.StatusFailState){
                        startOverInstall = true;
                        receiver.failWithNotification(ResourceEngineOutcomes.StatusFailState);
                    } else if (result == ResourceEngineOutcomes.StatusNoLocalStorage) {
                        startOverInstall = true;
                        receiver.failWithNotification(ResourceEngineOutcomes.StatusNoLocalStorage);
                    } else if(result == ResourceEngineOutcomes.StatusBadCertificate){
                        startOverInstall = false;
                        receiver.failWithNotification(ResourceEngineOutcomes.StatusBadCertificate);
                    } else {
                        startOverInstall = true;
                        receiver.failUnknown(ResourceEngineOutcomes.StatusFailUnknown);
                    }
                    
                    /*
                     * startOverInstall will be used on next install to indicate whether
                     * we want to start from the existing resource table or a new one,
                     * based on the outcome of this install
                     */
                    receiver.ccApp.getAppPreferences().edit().putBoolean(KEY_START_OVER, startOverInstall).commit();

                    /* 
                     * Check if we want to record this as a 'last install time', based on the 
                     * state of the resource table before and after this install took place
                     */
                    ResourceTable temporary = receiver.ccApp.getCommCarePlatform().getUpgradeResourceTable();
                    if (temporary.getTableReadiness() == ResourceTable.RESOURCE_TABLE_PARTIAL && 
                            receiver.resourceTableWasFresh) {
                        receiver.ccApp.getAppPreferences().edit().putLong(KEY_LAST_INSTALL, System.currentTimeMillis()).commit();
                    }

                }

                /*
                 * (non-Javadoc)
                 * @see org.commcare.android.tasks.templates.CommCareTask#deliverUpdate(java.lang.Object, java.lang.Object[])
                 */
                @Override
                protected void deliverUpdate(CommCareSetupActivity receiver, int[]... update) {
                    receiver.updateProgress(update[0][0], update[0][1], update[0][2]);
                }

                /*
                 * (non-Javadoc)
                 * @see org.commcare.android.tasks.templates.CommCareTask#deliverError(java.lang.Object, java.lang.Exception)
                 */
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

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MODE_BASIC, 0, Localization.get("menu.basic")).setIcon(android.R.drawable.ic_menu_help);
        menu.add(0, MODE_ADVANCED, 0, Localization.get("menu.advanced")).setIcon(android.R.drawable.ic_menu_edit);
        menu.add(0, MODE_ARCHIVE, 0, Localization.get("menu.archive")).setIcon(android.R.drawable.ic_menu_upload);
        return true;
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        
        MenuItem basic = menu.findItem(MODE_BASIC);
        MenuItem advanced = menu.findItem(MODE_ADVANCED);
        
        
        if(uiState == UiState.advanced) {
            basic.setVisible(true);
            advanced.setVisible(false);
        } else if(uiState == UiState.ready){
            basic.setVisible(false);
            advanced.setVisible(true);
        } else if(uiState == UiState.basic){
            basic.setVisible(false);
            advanced.setVisible(true);
        } else{
            basic.setVisible(false);
            advanced.setVisible(false);
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
        viewNotificationButton.setVisibility(View.GONE);
    }
    
    public void setModeToError(boolean canRetry){
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
        //mainMessage.setText(message);
        mainMessage.setText(MarkupUtil.getCustomSpannable(message));
        addressEntryButton.setVisibility(View.VISIBLE);
        advancedView.setVisibility(View.GONE);
        mScanBarcodeButton.setVisibility(View.VISIBLE);
        viewNotificationButton.setVisibility(View.GONE);
        startOverButton.setVisibility(View.GONE);
        installButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        retryButton.setText(Localization.get("install.button.retry"));
        startOverButton.setText(Localization.get("install.button.startover"));
    }

    public void setModeToAdvanced(){
        buttonView.setVisibility(View.VISIBLE);
        mainMessage.setText(Localization.get("install.manual"));
        advancedView.setVisibility(View.VISIBLE);
        mScanBarcodeButton.setVisibility(View.GONE);
        addressEntryButton.setVisibility(View.GONE);
        installButton.setVisibility(View.VISIBLE);
        startOverButton.setText(Localization.get("install.button.startover"));
        startOverButton.setVisibility(View.VISIBLE);
        installButton.setEnabled(true);
        viewNotificationButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        retryButton.setText(Localization.get("install.button.retry"));
        startOverButton.setText(Localization.get("install.button.startover"));
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MODE_BASIC:
            setUiState(UiState.basic);
            break;
        case MODE_ADVANCED:
            setUiState(UiState.advanced);
            break;
        case MODE_ARCHIVE:
             Intent i = new Intent(getApplicationContext(), InstallArchiveActivity.class);
             startActivityForResult(i, ARCHIVE_INSTALL);
             break;
        }
        
        refreshView();
        return true;
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
        
        setUiState(UiState.error);
        
        retryCount++;
        
        if(retryCount > RETRY_LIMIT){
            canRetry = false;
        }
        
        if(isAuto || alwaysNotify) {
            CommCareApplication._().reportNotificationMessage(message);
            viewNotificationButton.setVisibility(View.VISIBLE);
        }
        if(isAuto) {
            done(false);
        } else {
            if(alwaysNotify) {
                this.displayMessage= Localization.get("notification.for.details.setup.wrapper", new String[] {message.getDetails()});
                this.canRetry = canRetry;
                mainMessage.setText(displayMessage);
            } else {
                
                this.displayMessage= message.getDetails();
                this.canRetry = canRetry;
                
                String fullErrorMessage = message.getDetails();
                
                if(alwaysNotify){
                    fullErrorMessage = fullErrorMessage + message.getAction();
                }
                
                mainMessage.setText(fullErrorMessage);
            }
        }
        
        refreshView();
    }
    
    /**
     * Sets the state of the Ui and handles one-off changes that might need
     * to happen between certain states.
     * @param newUiState
     */
    public void setUiState(UiState newUiState){
        //We might want to do some one-off configuration
        //stuff if we're undergoing certain transitions
        
        //Ready -> Advanced: Set up the URL Spinner appropriately.
        if(this.uiState == UiState.ready && newUiState == UiState.advanced){
            previousUrlPosition = -1;
            urlSpinner.setSelection(2);
        }
        this.uiState = newUiState;
    }
    
    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onBackPressed()
     */
    @Override
    public void onBackPressed(){
        if(uiState == UiState.advanced || uiState == UiState.ready) {
            setUiState(UiState.basic);
            setModeToBasic();
        } else {
            super.onBackPressed();
        }
    }

    // All final paths from the Update are handled here (Important! Some interaction modes should always auto-exit this activity)
    // Everything here should call one of: fail() or done() 
    
    /** All methods for implementation of ResourceEngineListener **/
    
    public void reportSuccess(boolean appChanged) {
        //If things worked, go ahead and clear out any warnings to the contrary
        CommCareApplication._().clearNotifications("install_update");
        
        if(!appChanged) {
            Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
        }
        done(appChanged);
    }

    public void failMissingResource(UnresolvedResourceException ure, ResourceEngineOutcomes statusMissing) {
        fail(NotificationMessageFactory.message(statusMissing, new String[] {null, ure.getResource().getDescriptor(), ure.getMessage()}), ure.isMessageUseful());
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
    
    public void updateProgress(int done, int total, int phase) {
        if(inUpgradeMode) {       
            if (phase == ResourceEngineTask.PHASE_DOWNLOAD) {
                updateProgress(Localization.get("updates.found", new String[] {""+done,""+total}), DIALOG_INSTALL_PROGRESS);
            } else if (phase == ResourceEngineTask.PHASE_COMMIT) {
                updateProgress(Localization.get("updates.downloaded"), DIALOG_INSTALL_PROGRESS);
            }
        }
        else {
            updateProgress(Localization.get("profile.found", new String[]{""+done,""+total}), DIALOG_INSTALL_PROGRESS);
        }
    }

    public void failWithNotification(ResourceEngineOutcomes statusfailstate) {
        fail(NotificationMessageFactory.message(statusfailstate), true);
    }
    
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#generateProgressDialog(int)
     * 
     * implementation of generateProgressDialog() for DialogController --
     * all other methods handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId != DIALOG_INSTALL_PROGRESS) {
            System.out.println("WARNING: taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");        
            return null;
        }
        String title, message;
        if (uiState == UiState.upgrade) {
            title = Localization.get("updates.title");
            message = Localization.get("updates.checking");
        } else {
            title = Localization.get("updates.resources.initialization");
            message = Localization.get("updates.resources.profile");
        }
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
        String checkboxText = "Keep trying if network is interrupted";
        CustomProgressDialog lastDialog = getCurrentDialog();
        boolean isChecked = (lastDialog == null) ? false : lastDialog.isChecked();
        dialog.addCheckbox(checkboxText, isChecked);
        return dialog;
    }

}
