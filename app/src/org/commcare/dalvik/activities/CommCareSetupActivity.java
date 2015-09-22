package org.commcare.dalvik.activities;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.fragments.SetupEnterURLFragment;
import org.commcare.android.fragments.SetupInstallFragment;
import org.commcare.android.fragments.SetupKeepInstallFragment;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.BarcodeScanListenerDefaultImpl;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.ResourceEngineListener;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.android.util.SigningUtil;
import org.commcare.dalvik.BuildConfig;
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

import java.security.SignatureException;
import java.util.List;

/**
 * Responsible for identifying the state of the application (uninstalled,
 * installed) and performing any necessary setup to get to a place where
 * CommCare can load normally.
 *
 * If the startup activity identifies that the app is installed properly it
 * should not ever require interaction or be visible to the user.
 *
 * @author ctsims
 */
@ManagedUi(R.layout.first_start_screen_modern)
public class CommCareSetupActivity extends CommCareActivity<CommCareSetupActivity>
        implements ResourceEngineListener, SetupEnterURLFragment.URLInstaller,
        SetupKeepInstallFragment.StartStopInstallCommands {
    private static final String TAG = CommCareSetupActivity.class.getSimpleName();

    public static final String RESOURCE_STATE = "resource_state";
    public static final String KEY_PROFILE_REF = "app_profile_ref";
    public static final String KEY_UPGRADE_MODE = "app_upgrade_mode";
    public static final String KEY_ERROR_MODE = "app_error_mode";
    private static final String KEY_UI_STATE = "current_install_ui_state";
    private static final String KEY_OFFLINE =  "offline_install";
    private static final String KEY_FROM_EXTERNAL = "from_external";
    private static final String KEY_FROM_MANAGER = "from_manager";

    /**
     * Should the user be logged out when this activity is done?
     */
    public static final String KEY_REQUIRE_REFRESH = "require_referesh";
    public static final String KEY_INSTALL_FAILED = "install_failed";

    /**
     * Activity is being launched by auto update, instead of being triggered
     * manually.
     */
    public static final String KEY_AUTO = "is_auto_update";
    private static final String KEY_START_OVER = "start_over_uprgrade";
    private static final String KEY_LAST_INSTALL = "last_install_time";

    /**
     * UI configuration states.
     */
    public enum UiState {
        IN_URL_ENTRY,
        CHOOSE_INSTALL_ENTRY_METHOD,
        READY_TO_INSTALL,
        ERROR,

        /**
         * App installed already. Buttons aren't shown, trying to update app
         * with no user input
         */
        UPGRADE
    }

    private UiState uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;

    public static final int MODE_BASIC = Menu.FIRST;
    public static final int MODE_ADVANCED = Menu.FIRST + 1;
    private static final int MODE_ARCHIVE = Menu.FIRST + 2;
    private static final int MODE_SMS = Menu.FIRST + 3;

    public static final int BARCODE_CAPTURE = 1;
    private static final int ARCHIVE_INSTALL = 3;
    private static final int DIALOG_INSTALL_PROGRESS = 4;

    private boolean startAllowed = true;
    private boolean inUpgradeMode = false;
    private String incomingRef;
    private CommCareApp ccApp;

    /**
     * Indicates that this activity was launched from the AppManagerActivity
     */
    private boolean fromManager;

    /**
     * Indicates that this activity was launched from an outside application (such as a bit.ly
     * url entered in a browser)
     */
    private boolean fromExternal;

    /**
     * Indicates that the current install attempt will be made from a .ccz file, so we do
     * not need to check for internet connectivity
     */
    private boolean offlineInstall;

    /**
     * Whether this needs to be interactive (if it's automatic, we want to skip
     * a lot of the UI stuff
     */
    private boolean isAuto = false;

    /**
     * Keeps track of whether the previous resource table was in a 'fresh'
     * (empty or installed) state before the last install ran.
     */
    private boolean resourceTableWasFresh;

    private static final long START_OVER_THRESHOLD = 604800000; //1 week in milliseconds

    //region UIState fragments

    private final FragmentManager fm = getSupportFragmentManager();
    private final SetupKeepInstallFragment startInstall = new SetupKeepInstallFragment();
    private final SetupInstallFragment installFragment = new SetupInstallFragment();

    //endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CommCareSetupActivity oldActivity = (CommCareSetupActivity)this.getDestroyedActivityState();
        this.fromManager = this.getIntent().
                getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);

        //Retrieve instance state
        if(savedInstanceState == null) {
            Log.v("UiState", "SavedInstanceState is null, not getting anything from it =/");
            if(Intent.ACTION_VIEW.equals(this.getIntent().getAction())) {
                //We got called from an outside application, it's gonna be a wild ride!
                fromExternal = true;
                incomingRef = this.getIntent().getData().toString();
                if(incomingRef.contains(".ccz")) {
                    // make sure this is in the file system
                    boolean isFile = incomingRef.contains("file://");
                    if (isFile) {
                        // remove file:// prepend
                        incomingRef = incomingRef.substring(incomingRef.indexOf("//")+2);
                        Intent i = new Intent(this, InstallArchiveActivity.class);
                        i.putExtra(InstallArchiveActivity.ARCHIVE_REFERENCE, incomingRef);
                        startActivityForResult(i, ARCHIVE_INSTALL);
                    }
                    else{
                        // currently down allow other locations like http://
                        fail(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Bad_Archive_File), true);
                    }
                } else {
                    this.uiState = UiState.READY_TO_INSTALL;
                    //Now just start up normally.
                }
            } else {
                incomingRef = this.getIntent().getStringExtra(KEY_PROFILE_REF);
            }
            inUpgradeMode = this.getIntent().getBooleanExtra(KEY_UPGRADE_MODE, false);
            isAuto = this.getIntent().getBooleanExtra(KEY_AUTO, false);
        } else {
            String uiStateEncoded = savedInstanceState.getString(KEY_UI_STATE);
            this.uiState = uiStateEncoded == null ? UiState.CHOOSE_INSTALL_ENTRY_METHOD : UiState.valueOf(UiState.class, uiStateEncoded);
            Log.v("UiState","uiStateEncoded is: " + uiStateEncoded +
                    ", so my uiState is: " + uiState);
            incomingRef = savedInstanceState.getString("profileref");
            inUpgradeMode = savedInstanceState.getBoolean(KEY_UPGRADE_MODE);
            isAuto = savedInstanceState.getBoolean(KEY_AUTO);
            fromExternal = savedInstanceState.getBoolean(KEY_FROM_EXTERNAL);
            fromManager = savedInstanceState.getBoolean(KEY_FROM_MANAGER);
            offlineInstall = savedInstanceState.getBoolean(KEY_OFFLINE);
            // Uggggh, this might not be 100% legit depending on timing, what
            // if we've already reconnected and shut down the dialog?
            startAllowed = savedInstanceState.getBoolean("startAllowed");
        }
        // if we are in upgrade mode we want the UiState to reflect that,
        // unless we are showing an error
        if (inUpgradeMode && this.uiState != UiState.ERROR){
            this.uiState = UiState.UPGRADE;
        }

        // reclaim ccApp for resuming installation
        if(oldActivity != null) {
            this.ccApp = oldActivity.ccApp;
        }

        Log.v("UiState", "Current vars: " +
                        "UIState is: " + this.uiState + " " +
                        "incomingRef is: " + incomingRef + " " +
                        "startAllowed is: " + startAllowed + " "
        );

        uiStateScreenTransition();
        performSMSInstall(false);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                // removes the back button from the action bar
                actionBar.setDisplayHomeAsUpEnabled(false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If clicking the regular app icon brought us to CommCareSetupActivity
        // (because that's where we were last time the app was up), but there are now
        // 1 or more available apps, we want to redirect to CCHomeActivity
        if (!fromManager && !fromExternal && !inUpgradeMode &&
                CommCareApplication._().usableAppsPresent()) {
            Intent i = new Intent(this, CommCareHomeActivity.class);
            startActivity(i);
        }
    }
    
    @Override
    public void onURLChosen(String url) {
        if(BuildConfig.DEBUG) {
            Log.d(TAG, "SetupEnterURLFragment returned: " + url);
        }
        incomingRef = url;
        this.uiState = UiState.READY_TO_INSTALL;
        uiStateScreenTransition();
    }

    private void uiStateScreenTransition() {
        Fragment fragment;
        FragmentTransaction ft = fm.beginTransaction();

        switch (uiState){
            case UPGRADE:
            case READY_TO_INSTALL:
                if(incomingRef == null || incomingRef.length() == 0){
                    Log.e(TAG,"During install: IncomingRef is empty!");
                    Toast.makeText(getApplicationContext(), "Invalid URL: '" +
                            incomingRef + "'", Toast.LENGTH_SHORT).show();
                    return;
                }

                // the buttonCommands were already set when the fragment was
                // attached, no need to set them here
                fragment = startInstall;
                break;
            case IN_URL_ENTRY:
                fragment = restoreInstallSetupFragment();
                this.offlineInstall = false;
                break;
            case CHOOSE_INSTALL_ENTRY_METHOD:
                fragment = installFragment;
                this.offlineInstall = false;
                break;
            default:
                return;
        }
        ft.replace(R.id.setup_fragment_container, fragment);

        ft.commit();
    }

    private Fragment restoreInstallSetupFragment() {
        Fragment fragment = null;
        List<Fragment> fgmts = fm.getFragments();
        int lastIndex = fgmts != null ? fgmts.size() - 1 : -1;
        if(lastIndex > -1) {
            fragment = fgmts.get(lastIndex);
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Last fragment: " + fragment);
            }
        }
        if(!(fragment instanceof SetupEnterURLFragment)){
            // last fragment wasn't url entry, so default to the installation method chooser
            fragment = installFragment;
        }
        return fragment;
    }

    @Override
    protected void onStart() {
        super.onStart();
        uiStateScreenTransition();
        // upgrade app if needed
        if(uiState == UiState.UPGRADE &&
                incomingRef != null && incomingRef.length() != 0) {
            if (isNetworkNotConnected()){
                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Remote_NoNetwork, "INSTALL_NO_NETWORK"));
                finish();
            } else {
                startResourceInstall(true);
            }
        }
    }

    @Override
    protected int getWakeLockLevel() {
        return PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_UI_STATE, uiState.toString());
        outState.putString("profileref", incomingRef);
        outState.putBoolean(KEY_AUTO, isAuto);
        outState.putBoolean("startAllowed", startAllowed);
        outState.putBoolean(KEY_UPGRADE_MODE, inUpgradeMode);
        outState.putBoolean(KEY_OFFLINE, offlineInstall);
        outState.putBoolean(KEY_FROM_EXTERNAL, fromExternal);
        outState.putBoolean(KEY_FROM_MANAGER, fromManager);
        Log.v("UiState", "Saving instance state: " + outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String result = null;
        switch(requestCode) {
            case BARCODE_CAPTURE:
                if (resultCode == Activity.RESULT_OK) {
                    result = data.getStringExtra(BarcodeScanListenerDefaultImpl.SCAN_RESULT);
                    String dbg = "Got url from barcode scanner: " + result;
                    Log.i(TAG, dbg);
                }
                break;
            case ARCHIVE_INSTALL:
                if (resultCode == Activity.RESULT_OK) {
                    offlineInstall = true;
                    result = data.getStringExtra(InstallArchiveActivity.ARCHIVE_REFERENCE);
                }
                break;
        }
        if(result == null) return;
        incomingRef = result;
        this.uiState = UiState.READY_TO_INSTALL;

        try {
            // check if the reference can be derived without erroring out
            ReferenceManager._().DeriveReference(incomingRef);
        } catch (InvalidReferenceException ire) {
            // Couldn't process reference, return to basic ui state to ask user
            // for new install reference
            incomingRef = null;
            Toast.makeText(getApplicationContext(),
                    Localization.get("install.bad.ref"),
                    Toast.LENGTH_LONG).show();
            this.uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
        }
        uiStateScreenTransition();
    }

    private String getRef(){
        return incomingRef;
    }

    private CommCareApp getCommCareApp(){
        if (inUpgradeMode) {
            return CommCareApplication._().getCurrentApp();
        }

        ApplicationRecord newRecord =
            new ApplicationRecord(PropertyUtils.genUUID().replace("-",""),
                    ApplicationRecord.STATUS_UNINITIALIZED);

        return new CommCareApp(newRecord);
    }

    private void startResourceInstall() {
        CommCareApp ccApp = getCommCareApp();
        long lastInstallTime = ccApp.getAppPreferences().getLong(KEY_LAST_INSTALL, -1);
        if (System.currentTimeMillis() - lastInstallTime > START_OVER_THRESHOLD) {
            // If we are triggering a start over install due to the time
            // threshold when there is a partial resource table that we could
            // be using, send a message to log this.
            ResourceTable temporary = ccApp.getCommCarePlatform().getUpgradeResourceTable();
            if (temporary.getTableReadiness() == ResourceTable.RESOURCE_TABLE_PARTIAL) {
                Logger.log(AndroidLogger.TYPE_RESOURCES, "A start-over on installation has been "
                        + "triggered by the time threshold when there is an existing partial "
                        + "resource table that could be used.");
            }
            startResourceInstall(true);
        } else {
            startResourceInstall(ccApp.getAppPreferences().getBoolean(KEY_START_OVER, true));
        }
    }

    @Override
    public void startBlockingForTask(int id) {
        super.startBlockingForTask(id);
        this.startAllowed = false;
    }

    @Override
    public void stopBlockingForTask(int id) {
        super.stopBlockingForTask(id);
        this.startAllowed = true;
    }

    /**
     * @param startOverUpgrade When set CommCarePlatform.stageUpgradeTable()
     *                         will clear the last version of the upgrade table
     *                         and start over. Otherwise install reuses the
     *                         last version of the upgrade table.
     */
    private void startResourceInstall(boolean startOverUpgrade) {
        if(startAllowed) {
            CommCareApp app = getCommCareApp();
            ccApp = app;

            // store what the state of the resource table was before this
            // install, so we can compare it to the state after and decide if
            // this should count as a 'last install time'
            int tableStateBeforeInstall =
                ccApp.getCommCarePlatform().getUpgradeResourceTable().getTableReadiness();

            this.resourceTableWasFresh =
                (tableStateBeforeInstall == ResourceTable.RESOURCE_TABLE_EMPTY) ||
                (tableStateBeforeInstall == ResourceTable.RESOURCE_TABLE_INSTALLED);

            CustomProgressDialog lastDialog = getCurrentDialog();
             // used to tell the ResourceEngineTask whether or not it should
             // sleep before it starts, set based on whether we are currently
             // in keep trying mode.
            boolean shouldSleep = (lastDialog != null) && lastDialog.isChecked();

            ResourceEngineTask<CommCareSetupActivity> task =
                new ResourceEngineTask<CommCareSetupActivity>(inUpgradeMode,
                        app, startOverUpgrade,
                        DIALOG_INSTALL_PROGRESS, shouldSleep) {

                @Override
                protected void deliverResult(CommCareSetupActivity receiver,
                                             ResourceEngineOutcomes result) {
                    boolean startOverInstall = false;
                    switch (result) {
                        case StatusInstalled:
                            receiver.reportSuccess(true);
                            break;
                        case StatusUpToDate:
                            receiver.reportSuccess(false);
                            break;
                        case StatusMissingDetails:
                            // fall through to more general case:
                        case StatusMissing:
                            CustomProgressDialog lastDialog = receiver.getCurrentDialog();
                            if ((lastDialog != null) && lastDialog.isChecked()) {
                                // 'Keep Trying' installation mode is set
                                receiver.startResourceInstall(false);
                            } else {
                                receiver.failMissingResource(this.missingResourceException, result);
                            }
                            break;
                        case StatusBadReqs:
                            receiver.failBadReqs(badReqCode, vRequired, vAvailable, majorIsProblem);
                            break;
                        case StatusFailState:
                            startOverInstall = true;
                            receiver.failWithNotification(ResourceEngineOutcomes.StatusFailState);
                            break;
                        case StatusNoLocalStorage:
                            startOverInstall = true;
                            receiver.failWithNotification(ResourceEngineOutcomes.StatusNoLocalStorage);
                            break;
                        case StatusBadCertificate:
                            receiver.failWithNotification(ResourceEngineOutcomes.StatusBadCertificate);
                            break;
                        case StatusDuplicateApp:
                            startOverInstall = true;
                            receiver.failWithNotification(ResourceEngineOutcomes.StatusDuplicateApp);
                            break;
                        default:
                            startOverInstall = true;
                            receiver.failUnknown(ResourceEngineOutcomes.StatusFailUnknown);
                            break;
                     }

                    // Did the install fail in a way where the existing
                    // resource table should be reused in the next install
                    // attempt?
                    receiver.ccApp.getAppPreferences().edit().putBoolean(KEY_START_OVER, startOverInstall).commit();

                    // Check if we want to record this as a 'last install
                    // time', based on the state of the resource table before
                    // and after this install took place
                    ResourceTable temporary =
                        receiver.ccApp.getCommCarePlatform().getUpgradeResourceTable();

                    if (temporary.getTableReadiness() == ResourceTable.RESOURCE_TABLE_PARTIAL &&
                            receiver.resourceTableWasFresh) {
                        receiver.ccApp.getAppPreferences().edit().putLong(KEY_LAST_INSTALL, System.currentTimeMillis()).commit();
                    }
                }

                @Override
                protected void deliverUpdate(CommCareSetupActivity receiver,
                                             int[]... update) {
                    receiver.updateResourceProgress(update[0][0], update[0][1], update[0][2]);
                }

                @Override
                protected void deliverError(CommCareSetupActivity receiver,
                                            Exception e) {
                    receiver.failUnknown(ResourceEngineOutcomes.StatusFailUnknown);
                }
            };

            task.connect(this);
            task.execute(getRef());
        } else {
            Log.i(TAG, "During install: blocked a resource install press since a task was already running");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MODE_ARCHIVE, 0, Localization.get("menu.archive")).setIcon(android.R.drawable.ic_menu_upload);
        menu.add(0, MODE_SMS, 1, Localization.get("menu.sms")).setIcon(android.R.drawable.stat_notify_chat);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        return true;
    }

    /**
     * Scan SMS messages for texts with profile references.
     * @param installTriggeredManually if scan was triggered manually, then
     *                                 install automatically if reference is found
     */
    private void performSMSInstall(boolean installTriggeredManually){
        String profileLink = null;
        try {
            profileLink = this.scanSMSLinks();
        } catch(SignatureException e){
            // possibly we want to do something more severe here? could be malicious
            e.printStackTrace();
            Toast.makeText(this, Localization.get("menu.sms.not.verified"), Toast.LENGTH_LONG).show();
        }
        if (profileLink != null) {
            // we found a valid profile link, either start install automatically
            // or move to READY_TO_INSTALL state
            Log.v("install", "Performing SMS install with link : " + incomingRef);
            incomingRef = profileLink;
            if (installTriggeredManually) {
                startResourceInstall();
            } else {
                uiState = UiState.READY_TO_INSTALL;
                Toast.makeText(this, Localization.get("menu.sms.ready"), Toast.LENGTH_LONG).show();
            }
        } else if(installTriggeredManually) {
            // only notify if this was manually triggered, since most people won't use this
            Toast.makeText(this, Localization.get("menu.sms.not.found"), Toast.LENGTH_LONG).show();
        }
    }



    /**
     * Scan the SMS inbox, looking for messages that meet the expected install format,
     * and if found and verified return the discovered install link. Current behavior will search
     * backwards from the most recent text, returning the first discovered valid link
     * @return the verified install link, null if none found
     * @throws SignatureException if we discovered a valid-looking message but could not verifyMessageSignatureHelper it
     */
    private String scanSMSLinks() throws SignatureException{
        // http://stackoverflow.com/questions/11301046/search-sms-inbox
        final Uri SMS_INBOX = Uri.parse("content://sms/inbox");
        Cursor cursor = getContentResolver().query(SMS_INBOX, null, null, null, "date desc");
        try {
            if (cursor.moveToFirst()) { // must check the result to prevent exception
                while (!cursor.isAfterLast()) {
                    String textMessageBody = cursor.getString(cursor.getColumnIndex("body"));
                    if (textMessageBody.contains(GlobalConstants.SMS_INSTALL_KEY_STRING)) {
                        String installLink = SigningUtil.parseAndVerifySMS(textMessageBody);
                        if (installLink != null) {
                            return installLink;
                        }
                    }
                }
            }
        }
        finally{
            cursor.close();
        }
        return null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MODE_ARCHIVE) {
             Intent i = new Intent(getApplicationContext(), InstallArchiveActivity.class);
             startActivityForResult(i, ARCHIVE_INSTALL);
        }
        if (item.getItemId() == MODE_SMS) {
            performSMSInstall(true);
        }
        return true;
    }

    /**
     * Return to or launch home activity.
     *
     * @param requireRefresh should the user be logged out upon returning to
     *                       home activity?
     * @param failed did installation occur successfully?
     */
    void done(boolean requireRefresh, boolean failed) {
        if (Intent.ACTION_VIEW.equals(CommCareSetupActivity.this.getIntent().getAction())) {
            //Call out to CommCare Home
            Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
            i.putExtra(KEY_REQUIRE_REFRESH, requireRefresh);
            startActivity(i);
        } else {
            //Good to go
            Intent i = new Intent(getIntent());
            i.putExtra(KEY_REQUIRE_REFRESH, requireRefresh);
            i.putExtra(KEY_INSTALL_FAILED, failed);
            setResult(RESULT_OK, i);
        }
        finish();
    }

    /**
     * Raise failure message and return to the home activity with cancel code
     */
    void fail(NotificationMessage message, boolean alwaysNotify) {
        Toast.makeText(this, message.getTitle(), Toast.LENGTH_LONG).show();
        
        if (isAuto || alwaysNotify) {
            CommCareApplication._().reportNotificationMessage(message);
        }

        // Last install attempt failed, so restore to starting uistate to try again
        uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
        uiStateScreenTransition();
    }
    
    // All final paths from the Update are handled here (Important! Some
    // interaction modes should always auto-exit this activity) Everything here
    // should call one of: fail() or done() 
    
    /* All methods for implementation of ResourceEngineListener */
    
    @Override
    public void reportSuccess(boolean appChanged) {
        //If things worked, go ahead and clear out any warnings to the contrary
        CommCareApplication._().clearNotifications("install_update");
        
        if(!appChanged) {
            Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
        }
        done(appChanged, false);
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, ResourceEngineOutcomes statusMissing) {
        fail(NotificationMessageFactory.message(statusMissing, new String[] {null, ure.getResource().getDescriptor(), ure.getMessage()}), ure.isMessageUseful());
    }

    @Override
    public void failBadReqs(int code, String vRequired, String vAvailable, boolean majorIsProblem) {
        String versionMismatch = Localization.get("install.version.mismatch", new String[] {vRequired,vAvailable});
        
        String error;
        if (majorIsProblem){
            error=Localization.get("install.major.mismatch");
        } else {
            error=Localization.get("install.minor.mismatch");
        }
        
        fail(NotificationMessageFactory.message(ResourceEngineOutcomes.StatusBadReqs, new String[] {null, versionMismatch, error}), true);
    }

    @Override
    public void failUnknown(ResourceEngineOutcomes unknown) {
        fail(NotificationMessageFactory.message(unknown), false);
    }
    
    @Override
    public void updateResourceProgress(int done, int total, int phase) {
        if(inUpgradeMode) {       
            if (phase == ResourceEngineTask.PHASE_DOWNLOAD) {
                updateProgress(Localization.get("updates.found", new String[] {""+done,""+total}), DIALOG_INSTALL_PROGRESS);
            } else if (phase == ResourceEngineTask.PHASE_COMMIT) {
                updateProgress(Localization.get("updates.downloaded"), DIALOG_INSTALL_PROGRESS);
            }
        } else {
            updateProgress(Localization.get("profile.found", new String[]{""+done,""+total}), DIALOG_INSTALL_PROGRESS);
            updateProgressBar(done, total, DIALOG_INSTALL_PROGRESS);
        }
    }

    @Override
    public void failWithNotification(ResourceEngineOutcomes statusfailstate) {
        fail(NotificationMessageFactory.message(statusfailstate), true);
    }
    
    /**
     * {@inheritDoc}
     *
     * Implementation of generateProgressDialog() for DialogController --
     * all other methods handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId != DIALOG_INSTALL_PROGRESS) {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");
            return null;
        }
        String title, message;
        if (uiState == UiState.UPGRADE) {
            title = Localization.get("updates.title");
            message = Localization.get("updates.checking");
        } else {
            title = Localization.get("updates.resources.initialization");
            message = Localization.get("updates.resources.profile");
        }
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        String checkboxText = Localization.get("updates.keep.trying");
        CustomProgressDialog lastDialog = getCurrentDialog();
        boolean isChecked = (lastDialog != null) && lastDialog.isChecked();
        dialog.addCheckbox(checkboxText, isChecked);
        dialog.addProgressBar();
        return dialog;
    }

    //region StartStopInstallCommands implementation

    @Override
    public void onStartInstallClicked() {
        if (!offlineInstall && isNetworkNotConnected()) {
            failWithNotification(ResourceEngineOutcomes.StatusNoConnection);
        } else {
            startResourceInstall();
        }
    }

    @Override
    public void onStopInstallClicked() {
        incomingRef = null;
        uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
        uiStateScreenTransition();
    }

    public void setUiState(UiState newState) {
        uiState = newState;
    }

    //endregion
}
