package org.commcare.activities;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
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

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.fragments.ContainerFragment;
import org.commcare.fragments.InstallConfirmFragment;
import org.commcare.fragments.InstallPermissionsFragment;
import org.commcare.fragments.SelectInstallModeFragment;
import org.commcare.fragments.SetupEnterURLFragment;
import org.commcare.interfaces.RuntimePermissionRequester;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.preferences.GlobalPrivilegesManager;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.ResourceEngineTask;
import org.commcare.tasks.RetrieveParseVerifyMessageListener;
import org.commcare.tasks.RetrieveParseVerifyMessageTask;
import org.commcare.utils.ConsumerAppsUtil;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.MultipleAppsUtil;
import org.commcare.utils.Permissions;
import org.commcare.views.ManagedUi;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogCreationHelpers;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
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

    private static final String KEY_UI_STATE = "current_install_ui_state";
    private static final String KEY_LAST_INSTALL_MODE = "offline_install";
    private static final String KEY_FROM_EXTERNAL = "from_external";
    private static final String KEY_FROM_MANAGER = "from_manager";
    private static final String KEY_MANUAL_SMS_INSTALL = "sms-install-triggered-manually";

    private static final int SMS_PERMISSIONS_REQUEST = 2;

    private static final String FORCE_VALIDATE_KEY = "validate";

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
        READY_TO_INSTALL,
        NEEDS_PERMS,
        BLANK
    }

    private UiState uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;

    private static final int MODE_ARCHIVE = Menu.FIRST;
    private static final int MODE_SMS = Menu.FIRST + 2;

    // Activity request codes
    public static final int BARCODE_CAPTURE = 1;
    private static final int ARCHIVE_INSTALL = 3;
    private static final int MULTIPLE_APPS_LIMIT = 4;

    // dialog ID
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

    private static final int INSTALL_MODE_BARCODE = 0;
    private static final int INSTALL_MODE_URL = 1;
    private static final int INSTALL_MODE_OFFLINE = 2;
    private static final int INSTALL_MODE_SMS = 3;
    private int lastInstallMode;

    /**
     * Remember how the sms install was triggered in case orientation changes while asking for permissions
     */
    private boolean manualSMSInstall;

    private final FragmentManager fm = getSupportFragmentManager();
    private final InstallConfirmFragment startInstall = new InstallConfirmFragment();
    private final SelectInstallModeFragment installFragment = new SelectInstallModeFragment();
    private final InstallPermissionsFragment permFragment = new InstallPermissionsFragment();
    private ContainerFragment<CommCareApp> containerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.fromManager = this.getIntent().
                getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);

        if (checkForMultipleAppsViolation()) {
            return;
        }

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
                        i.putExtra(InstallArchiveActivity.ARCHIVE_FILEPATH, incomingRef);
                        startActivityForResult(i, ARCHIVE_INSTALL);
                    } else {
                        // currently down allow other locations like http://
                        fail(NotificationMessageFactory.message(NotificationMessageFactory.StockMessages.Bad_Archive_File), true);
                    }
                } else {
                    this.uiState = UiState.READY_TO_INSTALL;
                    //Now just start up normally.
                }
            }
        } else {
            loadStateFromInstance(savedInstanceState);
        }

        persistCommCareAppState();

        if (isSingleAppBuild()) {
            uiState = UiState.BLANK;
        }

        Log.v("UiState", "Current vars: " +
                "UIState is: " + this.uiState + " " +
                "incomingRef is: " + incomingRef + " " +
                "startAllowed is: " + startAllowed + " "
        );

        boolean askingForPerms =
                Permissions.acquireAllAppPermissions(this, this,
                        Permissions.ALL_PERMISSIONS_REQUEST);
        if (!askingForPerms) {
            if (isSingleAppBuild()) {
                SingleAppInstallation.installSingleApp(this, DIALOG_INSTALL_PROGRESS);
            } else {
                // With basic perms satisfied, ask user to allow SMS reading for sms app install code
                performSMSInstall(false);
            }
        }
    }

    private void loadStateFromInstance(Bundle savedInstanceState) {
        uiState = (UiState)savedInstanceState.getSerializable(KEY_UI_STATE);
        incomingRef = savedInstanceState.getString("profileref");
        fromExternal = savedInstanceState.getBoolean(KEY_FROM_EXTERNAL);
        fromManager = savedInstanceState.getBoolean(KEY_FROM_MANAGER);
        manualSMSInstall = savedInstanceState.getBoolean(KEY_MANUAL_SMS_INSTALL);
        lastInstallMode = savedInstanceState.getInt(KEY_LAST_INSTALL_MODE);
        // Uggggh, this might not be 100% legit depending on timing, what
        // if we've already reconnected and shut down the dialog?
        startAllowed = savedInstanceState.getBoolean("startAllowed");
    }

    private void persistCommCareAppState() {
        FragmentManager fm = this.getSupportFragmentManager();

        containerFragment = (ContainerFragment<CommCareApp>)fm.findFragmentByTag("cc-app");

        if (containerFragment == null) {
            containerFragment = new ContainerFragment<>();
            fm.beginTransaction().add(containerFragment, "cc-app").commit();
        } else {
            ccApp = containerFragment.getData();
        }
    }

    /**
     *
     * @return if installation is not allowed due to multiple apps limitations
     */
    private boolean checkForMultipleAppsViolation() {
        if (CommCareApplication._().getInstalledAppRecords().size() >= 2
                && !GlobalPrivilegesManager.isSuperuserPrivilegeEnabled()) {
            Intent i = new Intent(this, MultipleAppsLimitWarningActivity.class);
            i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, fromManager);
            startActivityForResult(i, MULTIPLE_APPS_LIMIT);
            return true;
        }
        return false;
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

        if (!fromManager && !fromExternal && MultipleAppsUtil.usableAppsPresent()) {
            // If clicking the regular app icon brought us to CommCareSetupActivity
            // (because that's where we were last time the app was up), but there are now
            // 1 or more available apps, we want to fall back to dispatch activity
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        uiStateScreenTransition();
    }

    @Override
    public void onURLChosen(String url) {
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
                    Log.e(TAG, "During install: incomingRef is empty!");
                    Toast.makeText(getApplicationContext(), "Empty URL provided",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // the buttonCommands were already set when the fragment was
                // attached, no need to set them here
                fragment = startInstall;
                break;
            case IN_URL_ENTRY:
                fragment = restoreInstallSetupFragment();
                break;
            case CHOOSE_INSTALL_ENTRY_METHOD:
                fragment = installFragment;
                break;
            case NEEDS_PERMS:
                fragment = permFragment;
                break;
            case BLANK:
                fragment = new Fragment();
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
        }
        if (!(fragment instanceof SetupEnterURLFragment)) {
            // last fragment wasn't url entry, so default to the installation method chooser
            fragment = installFragment;
        }
        return fragment;
    }

    @Override
    public int getWakeLockLevel() {
        return PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_UI_STATE, uiState);
        outState.putString("profileref", incomingRef);
        outState.putBoolean("startAllowed", startAllowed);
        outState.putInt(KEY_LAST_INSTALL_MODE, lastInstallMode);
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
                    lastInstallMode = INSTALL_MODE_BARCODE;
                }
                break;
            case ARCHIVE_INSTALL:
                if (resultCode == Activity.RESULT_OK) {
                    lastInstallMode = INSTALL_MODE_OFFLINE;
                    result = data.getStringExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE);
                }
                break;
            case MULTIPLE_APPS_LIMIT:
                setResult(RESULT_CANCELED);
                finish();
                return;
        }
        if (result == null) {
            return;
        }

        setReadyToInstall(result);
    }

    private void setReadyToInstall(String reference) {
        incomingRef = reference;
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

        if (lastInstallMode == INSTALL_MODE_OFFLINE) {
            onStartInstallClicked();
        } else {
            uiStateScreenTransition();
        }
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
            task.executeParallel(incomingRef);
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

    /**
     * Scan SMS messages for texts with profile references.
     *
     * @param installTriggeredManually if scan was triggered manually, then
     *                                 install automatically if reference is found
     */
    private void performSMSInstall(boolean installTriggeredManually) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            manualSMSInstall = installTriggeredManually;

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_SMS)) {
                DialogCreationHelpers.buildPermissionRequestDialog(this, this,
                                SMS_PERMISSIONS_REQUEST,
                                Localization.get("permission.sms.install.title"),
                                Localization.get("permission.sms.install.message")).showNonPersistentDialog();
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
    private void scanSMSLinks(final boolean installTriggeredManually) {
        // http://stackoverflow.com/questions/11301046/search-sms-inbox
        final Uri SMS_INBOX = Uri.parse("content://sms/inbox");

        DateTime oneDayAgo = (new DateTime()).minusDays(1);
        Cursor cursor = getContentResolver().query(SMS_INBOX,
                null, "date >? ",
                new String[]{"" + oneDayAgo.getMillis()},
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
                                            receiver.lastInstallMode = INSTALL_MODE_SMS;
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
                                            receiver.lastInstallMode = INSTALL_MODE_SMS;
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
                    mTask.executeParallel(textMessageBody);
                    break;
                }
            }
            // attemptedInstall will only be true if we found no texts with the SMS_INSTALL_KEY_STRING tag
            // if we found one, notification will be handle by the task receiver
            if (!attemptedInstall && installTriggeredManually) {
                Toast.makeText(this, Localization.get("menu.sms.not.found"), Toast.LENGTH_LONG).show();
            }
        } finally {
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

    private void fail(NotificationMessage message, boolean reportNotification) {
        String toastMessage;
        if (reportNotification) {
            CommCareApplication._().reportNotificationMessage(message);
            toastMessage = Localization.get("notification.for.details.wrapper", new String[]{message.getTitle()});
        } else {
            toastMessage = message.getTitle();
        }
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();

        // Last install attempt failed, so restore to starting uistate to try again
        uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
        uiStateScreenTransition();
    }

    // All final paths from the Update are handled here (Important! Some
    // interaction modes should always auto-exit this activity) Everything here
    // should call one of: fail() or reportSuccess()
    
    /* All methods for implementation of ResourceEngineListener */

    @Override
    public void reportSuccess(boolean newAppInstalled) {
        CommCareApplication._().clearNotifications("install_update");

        if (newAppInstalled) {
            GoogleAnalyticsUtils.reportAppInstall(lastInstallMode);
        } else {
            Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
        }

        if (Intent.ACTION_VIEW.equals(CommCareSetupActivity.this.getIntent().getAction())) {
            // app installed from external action
            if (getIntent().getBooleanExtra(FORCE_VALIDATE_KEY, false)) {
                // force multimedia validation to ensure app shows up in multiple apps list
                Intent i = new Intent(this, CommCareVerificationActivity.class);
                i.putExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, true);
                startActivity(i);
            } else {
                //Call out to CommCare Home
                Intent i = new Intent(getApplicationContext(), DispatchActivity.class);
                startActivity(i);
            }
        } else {
            Intent i = new Intent(getIntent());
            setResult(RESULT_OK, i);
        }
        finish();
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
        // perform safe localization because the localization dictionary might
        // be the resource currently being installed.
        if (!CommCareApplication._().isConsumerApp()) {
            // Don't change the text on the progress dialog if we are showing the generic consumer
            // apps startup dialog
            String installProgressText =
                    Localization.getWithDefault("profile.found",
                            new String[]{"" + done, "" + total},
                            "Application found. Loading resources...");
            updateProgress(installProgressText, DIALOG_INSTALL_PROGRESS);
        }
        updateProgressBar(done, total, DIALOG_INSTALL_PROGRESS);
    }

    @Override
    public void failWithNotification(AppInstallStatus statusFailState) {
        fail(NotificationMessageFactory.message(statusFailState), true);
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId != DIALOG_INSTALL_PROGRESS) {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");
            return null;
        }
        if (isSingleAppBuild()) {
            return ConsumerAppsUtil.getGenericConsumerAppsProgressDialog(taskId, true);
        } else {
            return generateNormalInstallDialog(taskId);
        }
    }

    private CustomProgressDialog generateNormalInstallDialog(int taskId) {
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

    @Override
    public void onStartInstallClicked() {
        if (lastInstallMode != INSTALL_MODE_OFFLINE && isNetworkNotConnected()) {
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
        if (UiState.IN_URL_ENTRY.equals(uiState)) {
            lastInstallMode = INSTALL_MODE_URL;
        }
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
        if (url != null) {
            incomingRef = url;
            uiState = UiState.READY_TO_INSTALL;
            uiStateScreenTransition();
            startResourceInstall();
        } else {
            // only notify if this was manually triggered, since most people won't use this
            Toast.makeText(this, Localization.get("menu.sms.not.found"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void exceptionReceived(Exception e) {
        if (e instanceof SignatureException) {
            e.printStackTrace();
            Toast.makeText(this, Localization.get("menu.sms.not.verified"), Toast.LENGTH_LONG).show();
        } else if (e instanceof IOException) {
            e.printStackTrace();
            Toast.makeText(this, Localization.get("menu.sms.not.retrieved"), Toast.LENGTH_LONG).show();
        } else {
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
            // external storage perms were enabled, so setup temp storage,
            // which fails in application setup without external storage perms.
            CommCareApplication._().prepareTemporaryStorage();
            if (!isSingleAppBuild()) {
                uiState = UiState.CHOOSE_INSTALL_ENTRY_METHOD;
                uiStateScreenTransition();
            }

            if (isSingleAppBuild()) {
                SingleAppInstallation.installSingleApp(this, DIALOG_INSTALL_PROGRESS);
            } else {
                // Since SMS asks for more permissions, call was delayed until here
                performSMSInstall(false);
            }
        }
    }

    private void showMissingPermissionState() {
        if (uiState != UiState.NEEDS_PERMS) {
            uiState = UiState.NEEDS_PERMS;
            uiStateScreenTransition();
        } else {
            InstallPermissionsFragment permFragment =
                    (InstallPermissionsFragment)getSupportFragmentManager().findFragmentById(R.id.setup_fragment_container);
            permFragment.updateDeniedState();
        }
    }

    public static String getAnalyticsActionFromInstallMode(int installModeCode) {
        switch(installModeCode) {
            case INSTALL_MODE_BARCODE:
                return GoogleAnalyticsFields.ACTION_BARCODE_INSTALL;
            case INSTALL_MODE_OFFLINE:
                return GoogleAnalyticsFields.ACTION_OFFLINE_INSTALL;
            case INSTALL_MODE_SMS:
                return GoogleAnalyticsFields.ACTION_SMS_INSTALL;
            case INSTALL_MODE_URL:
                return GoogleAnalyticsFields.ACTION_URL_INSTALL;
            default:
                return "";
        }
    }
}
