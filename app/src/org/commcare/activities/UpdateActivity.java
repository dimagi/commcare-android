package org.commcare.activities;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.interfaces.CommCareActivityUIController;
import org.commcare.interfaces.WithUIController;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.tasks.TaskListener;
import org.commcare.tasks.TaskListenerRegistrationException;
import org.commcare.tasks.UpdateTask;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.views.dialogs.CustomProgressDialog;
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
        implements TaskListener<Integer, AppInstallStatus>, WithUIController {

    private static final String TAG = UpdateActivity.class.getSimpleName();
    private static final String TASK_CANCELLING_KEY = "update_task_cancelling";
    private static final String IS_APPLYING_UPDATE_KEY = "applying_update_task_running";
    private static final int DIALOG_UPGRADE_INSTALL = 6;

    private boolean taskIsCancelling;
    private boolean isApplyingUpdate;
    private UpdateTask updateTask;
    private UpdateUIController uiController;

    private boolean proceedAutomatically;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController.setupUI();

        loadSaveInstanceState(savedInstanceState);

        boolean isRotation = savedInstanceState != null;
        setupUpdateTask(isRotation);

        proceedAutomatically = getIntent().getBooleanExtra(
                RefreshToLatestBuildActivity.FROM_LATEST_BUILD_UTIL, false);
        if (proceedAutomatically) {
            startUpdateCheck();
        }
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            taskIsCancelling =
                    savedInstanceState.getBoolean(TASK_CANCELLING_KEY, false);
            isApplyingUpdate =
                    savedInstanceState.getBoolean(IS_APPLYING_UPDATE_KEY, false);
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
        } else if (!isRotation && !taskIsCancelling) {
            startUpdateCheck();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!ConnectivityStatus.isNetworkAvailable(this) &&
                ConnectivityStatus.isAirplaneModeOn(this)) {
            uiController.noConnectivityUiState();
            return;
        }

        setUiFromTask();
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
    public void handleTaskCompletion(AppInstallStatus result) {
        if (result == AppInstallStatus.UpdateStaged) {
            uiController.unappliedUpdateAvailableUiState();
            if (proceedAutomatically) {
                launchUpdateInstallTask();
                return;
            }
        } else if (result == AppInstallStatus.UpToDate) {
            uiController.upToDateUiState();
            if (proceedAutomatically) {
                finishWithResult(RefreshToLatestBuildActivity.ALREADY_UP_TO_DATE);
                return;
            }
        } else {
            // Gives user generic failure warning; even if update staging
            // failed for a specific reason like xml syntax
            uiController.checkFailedUiState();
            if (proceedAutomatically) {
                finishWithResult(RefreshToLatestBuildActivity.UPDATE_ERROR);
                return;
            }
        }

        unregisterTask();
        uiController.refreshView();
    }

    private void finishWithResult(String result) {
        Intent i = new Intent();
        setResult(RESULT_OK, i);
        i.putExtra(RefreshToLatestBuildActivity.KEY_UPDATE_ATTEMPT_RESULT, result);
        finish();
    }

    @Override
    public void handleTaskCancellation(AppInstallStatus result) {
        unregisterTask();

        uiController.idleUiState();
    }

    protected void startUpdateCheck() {
        try {
            updateTask = UpdateTask.getNewInstance();
            updateTask.startPinnedNotification(this);
            updateTask.registerTaskListener(this);
        } catch (IllegalStateException e) {
            connectToRunningTask();
            return;
        } catch (TaskListenerRegistrationException e) {
            enterErrorState("Attempting to register a TaskListener to an " +
                    "already registered task.");
            return;
        }

        String ref = ResourceInstallUtils.getDefaultProfileRef();
        updateTask.execute(ref);
        uiController.downloadingUiState();
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
        task.execute();
        isApplyingUpdate = true;
        uiController.applyingUpdateUiState();
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId != DIALOG_UPGRADE_INSTALL) {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareSetupActivity");
            return null;
        }
        String title = Localization.get("updates.installing.title");
        String message = Localization.get("updates.installing.message");
        CustomProgressDialog dialog =
                CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        return dialog;
    }

    private void logoutOnSuccessfulUpdate() {
        final String upgradeFinishedText =
                Localization.get("updates.install.finished");
        Toast.makeText(this, upgradeFinishedText, Toast.LENGTH_LONG).show();
        CommCareApplication._().expireUserSession();
        if (proceedAutomatically) {
            finishWithResult(RefreshToLatestBuildActivity.UPDATE_SUCCESS);
        } else {
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
        uiController = new UpdateUIController(this, fromAppManager);
    }

    @Override
    public CommCareActivityUIController getUIController() {
        return this.uiController;
    }
}
