package org.commcare.dalvik.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerException;
import org.commcare.android.tasks.UpgradeAppTask;

/**
 * Allow user to manage app upgrading:
 *  - Check and downloading new latest upgrade
 *  - Stop an upgrade download
 *  - Apply a downloaded upgrade
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpgradeActivity extends CommCareActivity
        implements TaskListener<int[], Boolean> {

    private static final String TAG = UpgradeActivity.class.getSimpleName();
    private static final String UI_STATE_KEY = "ui_state";

    enum UpgradeUiState {
        idle,
        downloading,
        cancelling,
        unappliedInstall,
        error
    }
    private UpgradeUiState currentUiState;

    private UpgradeAppTask upgradeTask;

    private UpgradeUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadSaveInstanceState(savedInstanceState);

        uiController = new UpgradeUiController(this);
        uiController.setupUi();

        setupUpgradeTask();
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(UI_STATE_KEY)) {
                currentUiState =
                    (UpgradeUiState)savedInstanceState.get(UI_STATE_KEY);
            }
        }
    }

    private void setupUpgradeTask() {
        upgradeTask = UpgradeAppTask.getRunningInstance();

        try {
            if (upgradeTask != null) {
                upgradeTask.registerTaskListener(this);
                if (currentUiState != UpgradeUiState.cancelling) {
                    setUiStateFromRunningTask(upgradeTask.getStatus());
                }
            } else {
                currentUiState = pendingUpgradeOrIdle();
            }
        } catch (TaskListenerException e) {
            Log.e(TAG, "Attempting to register a TaskListener to an already " +
                            "registered task.");
            currentUiState = UpgradeUiState.error;
        }
    }

    private void setUiStateFromRunningTask(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                currentUiState = UpgradeUiState.downloading;
                break;
            case PENDING:
                currentUiState = pendingUpgradeOrIdle();
                break;
            case FINISHED:
                currentUiState = UpgradeUiState.error;
                break;
            default:
                currentUiState = UpgradeUiState.error;
        }
    }

    private UpgradeUiState pendingUpgradeOrIdle() {
        if (downloadedUpgradePresent()) {
            return UpgradeUiState.unappliedInstall;
        } else {
            return UpgradeUiState.idle;
        }
    }

    private boolean downloadedUpgradePresent() {
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        int currentProgress = 0;
        if (upgradeTask != null) {
            currentProgress = upgradeTask.getProgress();
        }
        uiController.updateUi(currentProgress, currentUiState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(UI_STATE_KEY, currentUiState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterTask();
    }

    @Override
    public void processTaskUpdate(int[]... vals) {
        int progress = vals[0][0];
        uiController.updateProgressBar(progress);
    }

    @Override
    public void processTaskResult(Boolean result) {
        if (result) {
            currentUiState = UpgradeUiState.unappliedInstall;
        } else {
            currentUiState = UpgradeUiState.idle;
        }

        uiController.updateButtonState(currentUiState);
        unregisterTask();
    }

    @Override
    public void processTaskCancel(Boolean result) {
        unregisterTask();

        currentUiState = UpgradeUiState.idle;
        uiController.updateUi(0, currentUiState);
    }

    private void unregisterTask() {
        if (upgradeTask != null) {
            try {
                upgradeTask.unregisterTaskListener(this);
            } catch (TaskListenerException e) {
                Log.e(TAG, "Attempting to unregister a not previously registered TaskListener.");
            }
            upgradeTask = null;
        }
    }

    protected void startUpgradeCheck() {
        try {
            upgradeTask = UpgradeAppTask.getNewInstance();
            upgradeTask.registerTaskListener(this);
        } catch (IllegalStateException e) {
            enterErrorState("There is already an existing upgrade task instance.");
            return;
        } catch (TaskListenerException e) {
            enterErrorState("Attempting to register a TaskListener to an " +
                    "already registered task.");
            return;
        }
        upgradeTask.execute("");
        // progressBar.setProgress(0);
        currentUiState = UpgradeUiState.downloading;
        uiController.updateButtonState(currentUiState);
    }

    private void enterErrorState(String errorMsg) {
        Log.e(TAG, errorMsg);
        currentUiState = UpgradeUiState.error;
        uiController.updateButtonState(currentUiState);
    }

    public void stopUpgradeCheck() {
        if (upgradeTask != null) {
            upgradeTask.cancel(true);
        }
        currentUiState = UpgradeUiState.cancelling;
        uiController.updateButtonState(currentUiState);
    }

}
