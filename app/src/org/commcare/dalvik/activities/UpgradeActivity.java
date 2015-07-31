package org.commcare.dalvik.activities;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.UiElement;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerException;
import org.commcare.android.tasks.UpgradeAppTask;
import org.commcare.dalvik.R;

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
    private static final String TASK_CANCELLING_KEY = "upgrade_task_is_cancelling";

    private boolean taskIsCancelling;

    private UpgradeAppTask upgradeTask;

    private UpgradeUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController = new UpgradeUiController(this);

        loadSaveInstanceState(savedInstanceState);

        uiController.setupUi();

        setupUpgradeTask();
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            taskIsCancelling =
                savedInstanceState.getBoolean(TASK_CANCELLING_KEY, false);
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
        uiController.setDownloadingButtonState();
        uiController.updateProgressBar(0);
    }

    private void enterErrorState(String errorMsg) {
        Log.e(TAG, errorMsg);
        uiController.setErrorButtonState();
    }

    public void stopUpgradeCheck() {
        if (upgradeTask != null) {
            upgradeTask.cancel(true);
            taskIsCancelling = true;
            uiController.setCancellingButtonState();
        } else {
            uiController.setIdleButtonState();
        }
    }

    public void setupUpgradeTask() {
        upgradeTask = UpgradeAppTask.getRunningInstance();

        try {
            if (upgradeTask != null) {
                upgradeTask.registerTaskListener(this);
            }
        } catch (TaskListenerException e) {
            Log.e(TAG, "Attempting to register a TaskListener to an already " +
                            "registered task.");
            uiController.setErrorButtonState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        int currentProgress = 0;
        if (upgradeTask != null) {
            currentProgress = upgradeTask.getProgress();
            if (taskIsCancelling) {
                uiController.setCancellingButtonState();
            } else {
                uiController.setUiStateFromRunningTask(upgradeTask.getStatus());
            }
        } else {
            uiController.pendingUpgradeOrIdle();
        }
        uiController.updateProgressBar(currentProgress);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TASK_CANCELLING_KEY, taskIsCancelling);
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
            uiController.setUnappliedInstallButtonState();
        } else {
            uiController.setIdleButtonState();
        }

        unregisterTask();
    }

    @Override
    public void processTaskCancel(Boolean result) {
        unregisterTask();

        uiController.setIdleButtonState();
        uiController.updateProgressBar(0);
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
}
