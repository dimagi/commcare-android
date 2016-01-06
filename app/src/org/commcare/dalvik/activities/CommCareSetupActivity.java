package org.commcare.dalvik.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.fragments.ContainerFragment;
import org.commcare.android.fragments.InstallConfirmFragment;
import org.commcare.android.fragments.SelectInstallModeFragment;
import org.commcare.android.fragments.SetupEnterURLFragment;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.Permissions;
import org.commcare.android.framework.RuntimePermissionRequester;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.notifications.NotificationMessage;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.resource.AppInstallStatus;
import org.commcare.android.resource.installers.SingleAppInstallation;
import org.commcare.android.tasks.ResourceEngineListener;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.RetrieveParseVerifyMessageListener;
import org.commcare.android.tasks.RetrieveParseVerifyMessageTask;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.dialogs.DialogCreationHelpers;
import org.commcare.resources.model.UnresolvedResourceException;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;
import org.joda.time.DateTime;

import java.io.IOException;
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
        InstallConfirmFragment.StartStopInstallCommands, RetrieveParseVerifyMessageListener,
        RuntimePermissionRequester {
    private static final String TAG = CommCareSetupActivity.class.getSimpleName();

    public static final String KEY_PROFILE_REF = "app_profile_ref";
    private static final String KEY_UI_STATE = "current_install_ui_state";
    private static final String KEY_OFFLINE =  "offline_install";
    private static final String KEY_FROM_EXTERNAL = "from_external";
    private static final String KEY_FROM_MANAGER = "from_manager";
    private static final String KEY_MANUAL_SMS_INSTALL = "sms-install-triggered-manually";

    private static final int SMS_PERMISSIONS_REQUEST = 2;

    /**
     * Should the user be logged out when this activity is done?
     */
    public static final String KEY_REQUIRE_REFRESH = "require_referesh";
    public static final String KEY_INSTALL_FAILED = "install_failed";

    /**
     * How many sms messages to scan over looking for commcare install link
     */
    private static final int SMS_CHECK_COUNT = 100;

    /**
     * UI configuration states.
     */
    public enum UiState {
        IN_URL_ENTRY,
        CHOOSE_INSTALL_ENTRY_METHOD,
        READY_TO_INSTALL
    }

    private UiState uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;

    private static final int MODE_ARCHIVE = Menu.FIRST;
    private static final int MODE_SMS = Menu.FIRST + 2;

    public static final int BARCODE_CAPTURE = 1;
    private static final int ARCHIVE_INSTALL = 3;
    private static final int DIALOG_INSTALL_PROGRESS = 4;

    private boolean startAllowed = true;
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
     * Remember how the sms install was triggered in case orientation changes while asking for permissions
     */
    private boolean manualSMSInstall;

    private final FragmentManager fm = getSupportFragmentManager();
    private final InstallConfirmFragment startInstall = new InstallConfirmFragment();
    private final SelectInstallModeFragment installFragment = new SelectInstallModeFragment();
    private ContainerFragment<CommCareApp> containerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.fromManager = this.getIntent().
                getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);

        //Retrieve instance state
        if (savedInstanceState == null) {
            Log.v("UiState", "SavedInstanceState is null, not getting anything from it =/");
            if (Intent.ACTION_VIEW.equals(this.getIntent().getAction())) {
                //We got called from an outside application, it's gonna be a wild ride!
                fromExternal = true;
                incomingRef = this.getIntent().getData().toString();
                if (incomingRef.contains(".ccz")) {
                    // make sure this is in the file system
                    boolean isFile = incomingRef.contains("file://");
                    if (isFile) {
                        // remove file:// prepend
                        incomingRef = incomingRef.substring(incomingRef.indexOf("//") + 2);
                        Intent i = new Intent(this, InstallArchiveActivity.class);
                        i.putExtra(InstallArchiveActivity.ARCHIVE_REFERENCE, incomingRef);
                        startActivityForResult(i, ARCHIVE_INSTALL);
                    } else {
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
        } else {
            loadStateFromInstance(savedInstanceState);
        }

        persistCommCareAppState();

        Log.v("UiState", "Current vars: " +
                        "UIState is: " + this.uiState + " " +
                        "incomingRef is: " + incomingRef + " " +
                        "startAllowed is: " + startAllowed + " "
        );

        Permissions.acquireAllAppPermissions(this, this, Permissions.ALL_PERMISSIONS_REQUEST);

        performSMSInstall(false);
    }

    private void loadStateFromInstance(Bundle savedInstanceState) {
        String uiStateEncoded = savedInstanceState.getString(KEY_UI_STATE);
        this.uiState = uiStateEncoded == null ? UiState.CHOOSE_INSTALL_ENTRY_METHOD : UiState.valueOf(UiState.class, uiStateEncoded);
        Log.v("UiState", "uiStateEncoded is: " + uiStateEncoded +
                ", so my uiState is: " + uiState);
        incomingRef = savedInstanceState.getString("profileref");
        fromExternal = savedInstanceState.getBoolean(KEY_FROM_EXTERNAL);
        fromManager = savedInstanceState.getBoolean(KEY_FROM_MANAGER);
        manualSMSInstall = savedInstanceState.getBoolean(KEY_MANUAL_SMS_INSTALL);
        offlineInstall = savedInstanceState.getBoolean(KEY_OFFLINE);
        // Uggggh, this might not be 100% legit depending on timing, what
        // if we've already reconnected and shut down the dialog?
        startAllowed = savedInstanceState.getBoolean("startAllowed");
    }

    private void persistCommCareAppState() {
        FragmentManager fm = this.getSupportFragmentManager();

        containerFragment = (ContainerFragment) fm.findFragmentByTag("cc-app");

        if (containerFragment == null) {
            containerFragment = new ContainerFragment<>();
            fm.beginTransaction().add(containerFragment, "cc-app").commit();
        } else {
            ccApp = containerFragment.getData();
        }
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
        if (!fromManager && !fromExternal &&
                CommCareApplication._().usableAppsPresent()) {
            Intent i = new Intent(this, DispatchActivity.class);
            startActivity(i);
        }

        if (isSingleAppBuild()) {
            SingleAppInstallation.installSingleApp(this, DIALOG_INSTALL_PROGRESS);
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        uiStateScreenTransition();
    }

    @Override
    public void onURLChosen(String url) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "SetupEnterURLFragment returned: " + url);
        }
        incomingRef = url;
        this.uiState = UiState.READY_TO_INSTALL;
        uiStateScreenTransition();
    }

    private void uiStateScreenTransition() {
        if (areFragmentsPaused()) {
            // Don't perform fragment transactions when the activity isn't visible
            return;
        }

        Fragment fragment;
        FragmentTransaction ft = fm.beginTransaction();

        switch (uiState) {
            case READY_TO_INSTALL:
                if (incomingRef == null || incomingRef.length() == 0) {
                    Log.e(TAG, "During install: IncomingRef is empty!");
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
        if (lastIndex > -1) {
            fragment = fgmts.get(lastIndex);
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Last fragment: " + fragment);
            }
        }
        if (!(fragment instanceof SetupEnterURLFragment)) {
            // last fragment wasn't url entry, so default to the installation method chooser
            fragment = installFragment;
        }
        return fragment;
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
        outState.putBoolean("startAllowed", startAllowed);
        outState.putBoolean(KEY_OFFLINE, offlineInstall);
        outState.putBoolean(KEY_FROM_EXTERNAL, fromExternal);
        outState.putBoolean(KEY_FROM_MANAGER, fromManager);
        outState.putBoolean(KEY_MANUAL_SMS_INSTALL, manualSMSInstall);
        Log.v("UiState", "Saving instance state: " + outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        String result = null;
        switch (requestCode) {
            case BARCODE_CAPTURE:
                if (resultCode == Activity.RESULT_OK) {
                    result = data.getStringExtra("SCAN_RESULT");
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
        if (result == null) return;
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

    private String getRef() {
        return incomingRef;
    }

    private CommCareApp getCommCareApp() {
        ApplicationRecord newRecord =
                new ApplicationRecord(PropertyUtils.genUUID().replace("-", ""),
                        ApplicationRecord.STATUS_UNINITIALIZED);

        return new CommCareApp(newRecord);
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

    private void startResourceInstall() {
        if (startAllowed) {
            ccApp = getCommCareApp();
            containerFragment.setData(ccApp);

            CustomProgressDialog lastDialog = getCurrentProgressDialog();
            // used to tell the ResourceEngineTask whether or not it should
            // sleep before it starts, set based on whether we are currently
            // in keep trying mode.
            boolean shouldSleep = (lastDialog != null) && lastDialog.isChecked();

            ResourceEngineTask<CommCareSetupActivity> task =
                    new ResourceEngineTask<CommCareSetupActivity>(ccApp,
                            DIALOG_INSTALL_PROGRESS, shouldSleep) {

                        @Override
                        protected void deliverResult(CommCareSetupActivity receiver,
                                                     AppInstallStatus result) {
                            switch (result) {
                                case Installed:
                                    receiver.reportSuccess(true);
                                    break;
                                case UpToDate:
                                    receiver.reportSuccess(false);
                                    break;
                                case MissingResourcesWithMessage:
                                    // fall through to more general case:
                                case MissingResources:
                                    receiver.failMissingResource(this.missingResourceException, result);
                                    break;
                                case IncompatibleReqs:
                                    receiver.failBadReqs(badReqCode, vRequired, vAvailable, majorIsProblem);
                                    break;
                                case NoLocalStorage:
                                    receiver.failWithNotification(AppInstallStatus.NoLocalStorage);
                                    break;
                                case BadCertificate:
                                    receiver.failWithNotification(AppInstallStatus.BadCertificate);
                                    break;
                                case DuplicateApp:
                                    receiver.failWithNotification(AppInstallStatus.DuplicateApp);
                                    break;
                                default:
                                    receiver.failUnknown(AppInstallStatus.UnknownFailure);
                                    break;
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
                            receiver.failUnknown(AppInstallStatus.UnknownFailure);
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
     *
     * @param installTriggeredManually if scan was triggered manually, then
     *                                 install automatically if reference is found
     */
    private void performSMSInstall(boolean installTriggeredManually){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            manualSMSInstall = installTriggeredManually;

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_SMS)) {
                AlertDialog dialog =
                        DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                SMS_PERMISSIONS_REQUEST,
                                Localization.get("permission.sms.install.title"),
                                Localization.get("permission.sms.install.message"));
                dialog.show();
            } else {
                requestNeededPermissions(SMS_PERMISSIONS_REQUEST);
            }
        } else {
            scanSMSLinks(installTriggeredManually);
        }
    }

    @Override
    public void requestNeededPermissions(int requestCode) {
        if (requestCode == SMS_PERMISSIONS_REQUEST) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_SMS},
                    requestCode);
        } else {
            ActivityCompat.requestPermissions(this,
                    Permissions.getAppPermissions(),
                    requestCode);
        }
    }

    /**
     * Scan the most recent incoming text messages for a message with a
     * verified link to a commcare app and install it.  Message scanning stops
     * after the number of scanned messages reaches 'SMS_CHECK_COUNT'.
     *
     * @param installTriggeredManually don't install the found app link
     */
    private void scanSMSLinks(final boolean installTriggeredManually){
        // http://stackoverflow.com/questions/11301046/search-sms-inbox
        final Uri SMS_INBOX = Uri.parse("content://sms/inbox");

        DateTime oneDayAgo = (new DateTime()).minusDays(1);
        Cursor cursor = getContentResolver().query(SMS_INBOX,
                null, "date >? ",
                new String[] {"" + oneDayAgo.getMillis() },
                "date DESC");

        if (cursor == null) {
            return;
        }
        int messageIterationCount = 0;
        try {
            boolean attemptedInstall = false;
            while (cursor.moveToNext() && messageIterationCount <= SMS_CHECK_COUNT) { // must check the result to prevent exception
                messageIterationCount++;
                String textMessageBody = cursor.getString(cursor.getColumnIndex("body"));
                if (textMessageBody.contains(GlobalConstants.SMS_INSTALL_KEY_STRING)) {
                    attemptedInstall = true;
                    RetrieveParseVerifyMessageTask mTask =
                            new RetrieveParseVerifyMessageTask<CommCareSetupActivity>(this, installTriggeredManually) {

                                @Override
                                protected void deliverResult(CommCareSetupActivity receiver, String result) {
                                    if (installTriggeredManually) {
                                        if (result != null) {
                                            receiver.incomingRef = result;
                                            receiver.uiState = UiState.READY_TO_INSTALL;
                                            receiver.uiStateScreenTransition();
                                            receiver.startResourceInstall();
                                        } else {
                                            // only notify if this was manually triggered, since most people won't use this
                                            Toast.makeText(receiver, Localization.get("menu.sms.not.found"), Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        if (result != null) {
                                            receiver.incomingRef = result;
                                            receiver.uiState = UiState.READY_TO_INSTALL;
                                            receiver.uiStateScreenTransition();
                                            Toast.makeText(receiver, Localization.get("menu.sms.ready"), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                }

                                @Override
                                protected void deliverUpdate(CommCareSetupActivity receiver, Void... update) {
                                    //do nothing for now
                                }

                                @Override
                                protected void deliverError(CommCareSetupActivity receiver, Exception e) {
                                    if (e instanceof SignatureException) {
                                        e.printStackTrace();
                                        Toast.makeText(receiver, Localization.get("menu.sms.not.verified"), Toast.LENGTH_LONG).show();
                                    } else if (e instanceof IOException) {
                                        e.printStackTrace();
                                        Toast.makeText(receiver, Localization.get("menu.sms.not.retrieved"), Toast.LENGTH_LONG).show();
                                    } else {
                                        e.printStackTrace();
                                        Toast.makeText(receiver, Localization.get("notification.install.unknown.title"), Toast.LENGTH_LONG).show();
                                    }
                                }
                            };
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, textMessageBody);
                    } else {
                        mTask.execute(textMessageBody);
                    }
                    break;
                }
            }
            // attemptedInstall will only be true if we found no texts with the SMS_INSTALL_KEY_STRING tag
            // if we found one, notification will be handle by the task receiver
            if(!attemptedInstall && installTriggeredManually) {
                Toast.makeText(this, Localization.get("menu.sms.not.found"), Toast.LENGTH_LONG).show();
            }
        }
        finally {
            cursor.close();
        }
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
     * Return to or launch dispatch activity.
     *
     * @param requireRefresh should the user be logged out upon returning to
     *                       home activity?
     * @param failed         did installation occur successfully?
     */
    private void done(boolean requireRefresh, boolean failed) {
        if (Intent.ACTION_VIEW.equals(CommCareSetupActivity.this.getIntent().getAction())) {
            //Call out to CommCare Home
            Intent i = new Intent(getApplicationContext(), DispatchActivity.class);
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
    private void fail(NotificationMessage message, boolean alwaysNotify) {
        Toast.makeText(this, message.getTitle(), Toast.LENGTH_LONG).show();

        if (alwaysNotify) {
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

        if (!appChanged) {
            Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
        }
        done(appChanged, false);
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusMissing) {
        fail(NotificationMessageFactory.message(statusMissing, new String[]{null, ure.getResource().getDescriptor(), ure.getMessage()}), ure.isMessageUseful());
    }

    @Override
    public void failBadReqs(int code, String vRequired, String vAvailable, boolean majorIsProblem) {
        String versionMismatch = Localization.get("install.version.mismatch", new String[]{vRequired, vAvailable});

        String error;
        if (majorIsProblem) {
            error = Localization.get("install.major.mismatch");
        } else {
            error = Localization.get("install.minor.mismatch");
        }

        fail(NotificationMessageFactory.message(AppInstallStatus.IncompatibleReqs, new String[]{null, versionMismatch, error}), true);
    }

    @Override
    public void failUnknown(AppInstallStatus unknown) {
        fail(NotificationMessageFactory.message(unknown), false);
    }

    @Override
    public void updateResourceProgress(int done, int total, int phase) {
        updateProgress(Localization.get("profile.found", new String[]{"" + done, "" + total}), DIALOG_INSTALL_PROGRESS);
        updateProgressBar(done, total, DIALOG_INSTALL_PROGRESS);
    }

    @Override
    public void failWithNotification(AppInstallStatus statusfailstate) {
        fail(NotificationMessageFactory.message(statusfailstate), true);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId != DIALOG_INSTALL_PROGRESS) {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");
            return null;
        }
        String title = Localization.get("updates.resources.initialization");
        String message = Localization.get("updates.resources.profile");
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        String checkboxText = Localization.get("install.keep.trying");
        CustomProgressDialog lastDialog = getCurrentProgressDialog();
        boolean isChecked = (lastDialog != null) && lastDialog.isChecked();
        dialog.addCheckbox(checkboxText, isChecked);
        dialog.addProgressBar();
        return dialog;
    }

    //region StartStopInstallCommands implementation

    @Override
    public void onStartInstallClicked() {
        if (!offlineInstall && isNetworkNotConnected()) {
            failWithNotification(AppInstallStatus.NoConnection);
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


    @Override
    public void downloadLinkReceived(String url) {
        if (url != null) {
            incomingRef = url;
            uiState = UiState.READY_TO_INSTALL;
            uiStateScreenTransition();
            Toast.makeText(this, Localization.get("menu.sms.ready"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void downloadLinkReceivedAutoInstall(String url) {
        if(url != null){
            incomingRef = url;
            uiState = UiState.READY_TO_INSTALL;
            uiStateScreenTransition();
            startResourceInstall();
        } else{
            // only notify if this was manually triggered, since most people won't use this
            Toast.makeText(this, Localization.get("menu.sms.not.found"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void exceptionReceived(Exception e) {
        if(e instanceof  SignatureException){
            e.printStackTrace();
            Toast.makeText(this, Localization.get("menu.sms.not.verified"), Toast.LENGTH_LONG).show();
        } else if(e instanceof IOException){
            e.printStackTrace();
            Toast.makeText(this, Localization.get("menu.sms.not.retrieved"), Toast.LENGTH_LONG).show();
        } else{
            e.printStackTrace();
            Toast.makeText(this, Localization.get("notification.install.unknown.title"), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * @return Is the build configured to automatically try to install an app
     * packaged up with the build without showing install options to the user.
     */
    private boolean isSingleAppBuild() {
        return BuildConfig.IS_SINGLE_APP_BUILD;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == SMS_PERMISSIONS_REQUEST) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.READ_SMS.equals(permissions[i]) &&
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    scanSMSLinks(manualSMSInstall);
                }
            }
        } else if (requestCode == Permissions.ALL_PERMISSIONS_REQUEST) {
            String[] requiredPerms = Permissions.getRequiredPerms();

            for (int i = 0; i < permissions.length; i++) {
                for (String requiredPerm : requiredPerms) {
                    if (requiredPerm.equals(permissions[i]) &&
                            grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        showMissingPermissionState();
                        return;
                    }
                }
            }
        }
    }

    private void showMissingPermissionState() {
        // TODO PLM: instead of popping up the same message we should disable
        // install buttons and show a message and button to re-request the
        // permissions.
        Permissions.acquireAllAppPermissions(this, this,
                Permissions.ALL_PERMISSIONS_REQUEST);
    }
}
