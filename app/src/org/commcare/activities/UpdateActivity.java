package org.commcare.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.util.Pair;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.android.nsd.MicroNode;
import org.commcare.android.nsd.NSDDiscoveryTools;
import org.commcare.android.nsd.NsdServiceListener;
import org.commcare.dalvik.BuildConfig;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.logging.AndroidLogger;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.TaskListener;
import org.commcare.tasks.TaskListenerRegistrationException;
import org.commcare.tasks.UpdateTask;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.ConsumerAppsUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.DialogChoiceItem;
import org.commcare.views.dialogs.PaneledChoiceDialog;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * Allow user to manage app updating:
 * - Check and download the latest update
 * - Stop a downloading update
 * - Apply a downloaded update
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateActivity extends CommCareActivity<UpdateActivity>
        implements TaskListener<Integer, ResultAndError<AppInstallStatus>>, WithUIController, NsdServiceListener {

    public static final String KEY_FROM_LATEST_BUILD_ACTIVITY = "from-test-latest-build-util";

    // Options menu codes
    public static final int MENU_UPDATE_TARGET_OPTIONS = Menu.FIRST;
    public static final int MENU_UPDATE_FROM_CCZ = Menu.FIRST + 1;
    public static final int MENU_UPDATE_FROM_HUB = Menu.FIRST + 2;

    // Activity request codes
    private static final int OFFLINE_UPDATE = 0;

    private static final String TAG = UpdateActivity.class.getSimpleName();
    private static final String TASK_CANCELLING_KEY = "update_task_cancelling";
    private static final String IS_APPLYING_UPDATE_KEY = "applying_update_task_running";
    private static final String IS_LOCAL_UPDATE = "is-local-update";
    public static final String OFFLINE_UPDATE_REF = "offline-update-ref";

    private static final int DIALOG_UPGRADE_INSTALL = 6;
    private static final int DIALOG_CONSUMER_APP_UPGRADE = 7;

    private boolean taskIsCancelling;
    private boolean isApplyingUpdate;
    private UpdateTask updateTask;
    private UpdateUIController uiController;

    private boolean proceedAutomatically;
    private boolean isLocalUpdate;
    private String offlineUpdateRef;

    private MicroNode.AppManifest hubAppRecord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        if (getIntent().getBooleanExtra(KEY_FROM_LATEST_BUILD_ACTIVITY, false)) {
            proceedAutomatically = true;
        } else if (CommCareApplication.instance().isConsumerApp()) {
            proceedAutomatically = true;
            isLocalUpdate = true;
        }

        loadSavedInstanceState(savedInstanceState);

        boolean isRotation = savedInstanceState != null;
        setupUpdateTask(isRotation);
    }

    private void loadSavedInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            taskIsCancelling = savedInstanceState.getBoolean(TASK_CANCELLING_KEY, false);
            isApplyingUpdate = savedInstanceState.getBoolean(IS_APPLYING_UPDATE_KEY, false);
            isLocalUpdate = savedInstanceState.getBoolean(IS_LOCAL_UPDATE, false);
            offlineUpdateRef = savedInstanceState.getString(OFFLINE_UPDATE_REF);
            uiController.loadSavedUIState(savedInstanceState);
        }
    }

    private void setupUpdateTask(boolean isRotation) {
        updateTask = UpdateTask.getRunningInstance();

        if (updateTask != null) {
            try {
                updateTask.registerTaskListener(this);
            } catch (TaskListenerRegistrationException e) {
                Log.e(TAG, "Attempting to register a TaskListener to an already " +
                        "registered task.");
                uiController.errorUiState();
            }
        } else if (!isRotation && !taskIsCancelling
                && (ConnectivityStatus.isNetworkAvailable(this) || offlineUpdateRef != null)) {
            startUpdateCheck();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        NSDDiscoveryTools.registerForNsdServices(this, this);

        if (!ConnectivityStatus.isNetworkAvailable(this) && offlineUpdateRef == null) {
            uiController.noConnectivityUiState();
            return;
        }

        setUiFromTask();
    }

    @Override
    protected void onPause() {
        super.onPause();

        NSDDiscoveryTools.unregisterForNsdServices(this);
    }

    private void setUiFromTask() {
        if (updateTask != null) {
            if (taskIsCancelling) {
                uiController.cancellingUiState();
            } else {
                setUiStateFromTaskStatus(updateTask.getStatus());
            }

            int currentProgress = updateTask.getProgress();
            int maxProgress = updateTask.getMaxProgress();
            uiController.updateProgressBar(currentProgress, maxProgress);
        } else {
            setPendingUpdate();
        }
        uiController.refreshView();
    }

    private void setUiStateFromTaskStatus(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                uiController.downloadingUiState();
                break;
            case PENDING:
                break;
            case FINISHED:
                uiController.errorUiState();
                break;
            default:
                uiController.errorUiState();
        }
    }

    private void setPendingUpdate() {
        if (!isApplyingUpdate && ResourceInstallUtils.isUpdateReadyToInstall()) {
            uiController.unappliedUpdateAvailableUiState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterTask();
    }

    private void unregisterTask() {
        if (updateTask != null) {
            try {
                updateTask.unregisterTaskListener(this);
            } catch (TaskListenerRegistrationException e) {
                Log.e(TAG, "Attempting to unregister a not previously " +
                        "registered TaskListener.");
            }
            updateTask = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TASK_CANCELLING_KEY, taskIsCancelling);
        outState.putBoolean(IS_APPLYING_UPDATE_KEY, isApplyingUpdate);
        outState.putString(OFFLINE_UPDATE_REF, offlineUpdateRef);
        outState.putBoolean(IS_LOCAL_UPDATE, isLocalUpdate);
        uiController.saveCurrentUIState(outState);
    }

    @Override
    public void handleTaskUpdate(Integer... vals) {
        int progress = vals[0];
        int max = vals[1];
        uiController.updateProgressBar(progress, max);
        String msg = Localization.get("updates.found",
                new String[]{"" + progress, "" + max});
        uiController.updateProgressText(msg);
    }

    @Override
    public void handleTaskCompletion(ResultAndError<AppInstallStatus> result) {
        if (CommCareApplication.instance().isConsumerApp()) {
            dismissProgressDialog();
        }

        if (result.data == AppInstallStatus.UpdateStaged) {
            uiController.unappliedUpdateAvailableUiState();
            if (proceedAutomatically) {
                launchUpdateInstallTask();
            }
        } else if (result.data == AppInstallStatus.UpToDate) {
            uiController.upToDateUiState();
            if (proceedAutomatically) {
                finishWithResult(RefreshToLatestBuildActivity.ALREADY_UP_TO_DATE);
            }
        } else {
            reportFailureToNotifications(result.errorMessage);
            uiController.checkFailedUiState();
            if (proceedAutomatically) {
                finishWithResult(RefreshToLatestBuildActivity.UPDATE_ERROR);
            }
        }

        unregisterTask();
        uiController.refreshView();
    }

    private void reportFailureToNotifications(String errorMessage) {
        NotificationMessage notificationMessage = null;

        if (UpdateTask.isCombinedErrorMessage(errorMessage)) {
            Pair<String, String> resourceAndMessage =
                    UpdateTask.splitCombinedErrorMessage(errorMessage);
            notificationMessage =
                    NotificationMessageFactory.message(AppInstallStatus.InvalidResource,
                            new String[]{null, resourceAndMessage.first, resourceAndMessage.second});
        } else if (!"".equals(errorMessage)) {
            notificationMessage =
                    NotificationMessageFactory.message(AppInstallStatus.UpdateFailedGeneral,
                            new String[]{null, errorMessage, null});
        }

        if (notificationMessage != null) {
            CommCareApplication.notificationManager()
                    .reportNotificationMessage(notificationMessage, true);
        }
    }

    private void finishWithResult(String result) {
        Intent i = new Intent();
        setResult(RESULT_OK, i);
        i.putExtra(RefreshToLatestBuildActivity.KEY_UPDATE_ATTEMPT_RESULT, result);
        finish();
    }

    @Override
    public void handleTaskCancellation() {
        unregisterTask();

        uiController.idleUiState();
    }

    protected void startUpdateCheck() {
        try {
            updateTask = UpdateTask.getNewInstance();
            initUpdateTaskProgressDisplay();
            if (isLocalUpdate) {
                updateTask.setLocalAuthority();
            }
            updateTask.registerTaskListener(this);
        } catch (IllegalStateException e) {
            connectToRunningTask();
            return;
        } catch (TaskListenerRegistrationException e) {
            enterErrorState("Attempting to register a TaskListener to an " +
                    "already registered task.");
            return;
        }

        String profileRef;
        if (hubAppRecord != null && offlineUpdateRef != null) {
            updateTask.setLocalAuthority();
        }

        if (offlineUpdateRef != null) {
            profileRef = offlineUpdateRef;
            offlineUpdateRef = null;
        } else {
            profileRef = ResourceInstallUtils.getDefaultProfileRef();
        }
        uiController.downloadingUiState();
        updateTask.executeParallel(profileRef);
    }

    /**
     * Since updates in a consumer app do not use the normal UpdateActivity UI, use an
     * alternative method of displaying the update check's progress in that case
     */
    private void initUpdateTaskProgressDisplay() {
        if (CommCareApplication.instance().isConsumerApp()) {
            showProgressDialog(DIALOG_CONSUMER_APP_UPGRADE);
        } else {
            updateTask.startPinnedNotification(this);
        }
    }

    private void connectToRunningTask() {
        setupUpdateTask(false);

        setUiFromTask();
    }

    private void enterErrorState(String errorMsg) {
        Log.e(TAG, errorMsg);
        uiController.errorUiState();
    }

    public void stopUpdateCheck() {
        if (updateTask != null) {
            updateTask.cancelWasUserTriggered();
            updateTask.cancel(true);
            taskIsCancelling = true;
            uiController.cancellingUiState();
        } else {
            uiController.idleUiState();
        }

        if (proceedAutomatically) {
            finishWithResult(RefreshToLatestBuildActivity.UPDATE_CANCELED);
        }
    }

    /**
     * Block the user with a dialog while the update is finalized.
     */
    protected void launchUpdateInstallTask() {
        InstallStagedUpdateTask<UpdateActivity> task =
                new InstallStagedUpdateTask<UpdateActivity>(DIALOG_UPGRADE_INSTALL) {

                    @Override
                    protected void deliverResult(UpdateActivity receiver,
                                                 AppInstallStatus result) {
                        if (result == AppInstallStatus.Installed) {
                            reportAppUpdate();
                            receiver.logoutOnSuccessfulUpdate();
                        } else {
                            if (proceedAutomatically) {
                                finishWithResult(RefreshToLatestBuildActivity.UPDATE_ERROR);
                                return;
                            }
                            receiver.uiController.errorUiState();
                        }
                        receiver.isApplyingUpdate = false;
                    }

                    @Override
                    protected void deliverUpdate(UpdateActivity receiver,
                                                 int[]... update) {
                    }

                    @Override
                    protected void deliverError(UpdateActivity receiver,
                                                Exception e) {
                        receiver.uiController.errorUiState();
                        receiver.isApplyingUpdate = false;
                    }
                };
        task.connect(this);
        task.executeParallel();
        isApplyingUpdate = true;
        uiController.applyingUpdateUiState();
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (CommCareApplication.instance().isConsumerApp()) {
            return ConsumerAppsUtil.getGenericConsumerAppsProgressDialog(taskId, false);
        } else if (taskId != DIALOG_UPGRADE_INSTALL) {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");
            return null;
        } else {
            return generateNormalUpdateInstallDialog(taskId);
        }
    }

    private static CustomProgressDialog generateNormalUpdateInstallDialog(int taskId) {
        String title = Localization.get("updates.installing.title");
        String message = Localization.get("updates.installing.message");
        CustomProgressDialog dialog =
                CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        return dialog;
    }

    private static void reportAppUpdate() {
        try {
            String username = CommCareApplication.instance().getRecordForCurrentUser().getUsername();
            String updateLogMessage = "User " + username + " updated to app version " +
                    ReportingUtils.getAppBuildNumber();
            Logger.log(AndroidLogger.TYPE_USER, updateLogMessage);
        } catch (SessionUnavailableException e) {
            // Must be updating from the app manager, in which case we don't have a current use to
            // report for
        }

    }

    private void logoutOnSuccessfulUpdate() {
        final String upgradeFinishedText =
                Localization.get("updates.install.finished");
        CommCareApplication.instance().expireUserSession();
        if (proceedAutomatically) {
            finishWithResult(RefreshToLatestBuildActivity.UPDATE_SUCCESS);
        } else {
            Toast.makeText(this, upgradeFinishedText, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            this.finish();
        }
    }

    @Override
    public String getActivityTitle() {
        return "Update" + super.getActivityTitle();
    }

    @Override
    public void initUIController() {
        boolean fromAppManager = getIntent().getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);
        if (CommCareApplication.instance().isConsumerApp()) {
            uiController = new BlankUpdateUIController(this, fromAppManager);
        } else {
            uiController = new UpdateUIController(this, fromAppManager);
        }
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_UPDATE_TARGET_OPTIONS, 0, Localization.get("menu.update.options"));
        menu.add(0, MENU_UPDATE_FROM_CCZ, 1, Localization.get("menu.update.from.ccz"));
        menu.add(0, MENU_UPDATE_FROM_HUB, 2, Localization.get("menu.update.from.hub"));
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(MENU_UPDATE_FROM_CCZ).setVisible(BuildConfig.DEBUG ||
                !getIntent().getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false));
        menu.findItem(MENU_UPDATE_FROM_HUB).setVisible(hubAppRecord != null);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_UPDATE_FROM_CCZ:
                Intent i = new Intent(getApplicationContext(), InstallArchiveActivity.class);
                i.putExtra(InstallArchiveActivity.FROM_UPDATE, true);
                startActivityForResult(i, OFFLINE_UPDATE);
                return true;
            case MENU_UPDATE_FROM_HUB:
                triggerLocalHubUpdate();
                return true;
            case MENU_UPDATE_TARGET_OPTIONS:
                showUpdateTargetChoiceDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    private void triggerLocalHubUpdate() {
        offlineUpdateRef = hubAppRecord.getLocalUrl();
        this.startUpdateCheck();
    }

    private void showUpdateTargetChoiceDialog() {
        final PaneledChoiceDialog dialog =
                new PaneledChoiceDialog(this, Localization.get("menu.update.options"));

        DialogChoiceItem latestStarredChoice = new DialogChoiceItem(
                Localization.get("update.option.starred"), -1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommCarePreferences.setUpdateTarget(CommCarePreferences.UPDATE_TARGET_STARRED);
                dialog.dismiss();
            }
        });

        DialogChoiceItem latestBuildChoice = new DialogChoiceItem(
                Localization.get("update.option.build"), -1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommCarePreferences.setUpdateTarget(CommCarePreferences.UPDATE_TARGET_BUILD);
                dialog.dismiss();
            }
        });

        DialogChoiceItem latestSavedChoice = new DialogChoiceItem(
                Localization.get("update.option.saved"), -1, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CommCarePreferences.setUpdateTarget(CommCarePreferences.UPDATE_TARGET_SAVED);
                dialog.dismiss();
            }
        });

        dialog.setChoiceItems(
                new DialogChoiceItem[]{latestStarredChoice, latestBuildChoice, latestSavedChoice});
        this.showAlertDialog(dialog);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case OFFLINE_UPDATE:
                if (resultCode == Activity.RESULT_OK) {
                    offlineUpdateRef = intent.getStringExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE);
                    if (offlineUpdateRef != null) {
                        isLocalUpdate = true;
                        setupUpdateTask(false);
                    }
                }
                break;
        }
    }

    private void notifyLocalUpdatePathAvailable(MicroNode.AppManifest hubAppRecord) {
        this.hubAppRecord = hubAppRecord;
        this.rebuildOptionsMenu();
    }


    @Override
    public synchronized void onMicronodeDiscovery() {
        boolean appsAvailable = false;

        //If we aren't staged, don't go down this road
        if (CommCareApplication.instance().getCurrentApp() == null) {
            return;
        }

        String appId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        for (MicroNode node : NSDDiscoveryTools.getAvailableMicronodes()) {
            final MicroNode.AppManifest appManifest = node.getManifestForAppId(appId);
            if (appManifest != null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyLocalUpdatePathAvailable(appManifest);
                    }
                });
            }
        }
    }

}
