package org.commcare.dalvik.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import in.uncod.android.bypass.Bypass;

import java.io.StringReader;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.fragments.SetupEnterURLFragment;
import org.commcare.android.fragments.SetupInstallFragment;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
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
import android.text.method.LinkMovementMethod;
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
@ManagedUi(R.layout.first_start_screen_modern)
public class CommCareSetupActivity extends CommCareActivity<CommCareSetupActivity> implements ResourceEngineListener, SetupEnterURLFragment.URLInstaller {
    
//    public static final String DATABASE_STATE = "database_state";
    public static final String RESOURCE_STATE = "resource_state";
    public static final String KEY_PROFILE_REF = "app_profile_ref";
    public static final String KEY_UPGRADE_MODE = "app_upgrade_mode";
    public static final String KEY_ERROR_MODE = "app_error_mode";
    public static final String KEY_REQUIRE_REFRESH = "require_referesh";
    public static final String KEY_AUTO = "is_auto_update";
    public static final String KEY_START_OVER = "start_over_uprgrade";
    public static final String KEY_LAST_INSTALL = "last_install_time";

    @Override
    public void OnURLChosen(String url) {
        Log.d("DEBUG-d", "SetupEnterURLFragment returned: " + url);
        incomingRef = url;
        startResourceInstall();
    }
    
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
    
    public static final int MODE_ARCHIVE = Menu.FIRST + 2;
    
    public static final int BARCODE_CAPTURE = 1;
    public static final int MISSING_MEDIA_ACTIVITY=2;
    public static final int ARCHIVE_INSTALL = 3;
    public static final int DIALOG_INSTALL_PROGRESS = 4; 

    
    public static final int RETRY_LIMIT = 20;
    
    boolean startAllowed = true;
    boolean inUpgradeMode = false;
    
    int retryCount=0;
    
    public final boolean doStyle = true;
    
    
    public String incomingRef;
    public boolean canRetry;
    public String displayMessage;

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

        SetupInstallFragment installFragment = new SetupInstallFragment();

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        ft.replace(R.id.setup_fragment_container, installFragment);

        ft.commit();
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
    
    /*
     * (non-Javadoc)
     * @see android.support.v4.app.FragmentActivity#onStart()
     */
    @Override
    protected void onStart() {
        super.onStart();
        startResourceInstall();
        //Moved here to properly attach fragments and such.
        //NOTE: May need to do so elsewhere as well
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
        String result = null;
        switch(requestCode) {
            case BARCODE_CAPTURE:
                if (resultCode == Activity.RESULT_OK) {
                    result = data.getStringExtra("SCAN_RESULT");
                    String dbg = "Got url from barcode scanner: " + result;
                    Log.i("DEBUG-i",dbg);
                }
                break;
            case ARCHIVE_INSTALL:
                if (resultCode == Activity.RESULT_OK) {
                    result = data.getStringExtra(InstallArchiveActivity.ARCHIVE_REFERENCE);
                }
                break;
        }
        if(result == null) return;
        incomingRef = result;
        //Definitely have a URI now.
        try {
            ReferenceManager._().DeriveReference(incomingRef);
        } catch (InvalidReferenceException ire) {
            return;
        }
    }

    private String getRef(){
        return incomingRef;
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
        
        return true;
    }
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MODE_ARCHIVE:
             Intent i = new Intent(getApplicationContext(), InstallArchiveActivity.class);
             startActivityForResult(i, ARCHIVE_INSTALL);
             break;
        }
        
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
                this.displayMessage= Localization.get("notification.for.details.setup.wrapper", new String[] {message.getDetails()});
                this.canRetry = canRetry;
            } else {
                
                this.displayMessage= message.getDetails();
                this.canRetry = canRetry;
                
                String fullErrorMessage = message.getDetails();
                
                if(alwaysNotify){
                    fullErrorMessage = fullErrorMessage + message.getAction();
                }
            }
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
            updateProgressBar(done, total, DIALOG_INSTALL_PROGRESS);
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
        String checkboxText = Localization.get("updates.keep.trying");;
        CustomProgressDialog lastDialog = getCurrentDialog();
        boolean isChecked = (lastDialog == null) ? false : lastDialog.isChecked();
        dialog.addCheckbox(checkboxText, isChecked);
        dialog.addProgressBar();
        return dialog;
    }

}
