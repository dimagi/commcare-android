package org.commcare.dalvik.activities;

import android.os.Bundle;
import android.util.Log;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.tasks.ResourceEngineOutcomes;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerException;
import org.commcare.android.tasks.UpgradeTask;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.resources.model.ResourceTable;

/**
 * Allow user to manage app upgrading:
 * - Check and download the latest upgrade
 * - Stop a downloading upgrade
 * - Apply a downloaded upgrade
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpgradeActivity extends CommCareActivity
        implements TaskListener<int[], ResourceEngineOutcomes> {

    private static final String TAG = UpgradeActivity.class.getSimpleName();
    private static final String TASK_CANCELLING_KEY = "upgrade_task_cancelling";

    private boolean taskIsCancelling;
    private boolean resourceTableWasFresh;

    private UpgradeTask upgradeTask;

    private UpgradeUiController uiController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController = new UpgradeUiController(this);

        loadSaveInstanceState(savedInstanceState);

        setupUpgradeTask();
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            taskIsCancelling =
                    savedInstanceState.getBoolean(TASK_CANCELLING_KEY, false);
        }
    }

    public void setupUpgradeTask() {
        upgradeTask = UpgradeTask.getRunningInstance();

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
    protected void onDestroy() {
        super.onDestroy();

        unregisterTask();
    }

    private void unregisterTask() {
        if (upgradeTask != null) {
            try {
                upgradeTask.unregisterTaskListener(this);
            } catch (TaskListenerException e) {
                Log.e(TAG, "Attempting to unregister a not previously " +
                        "registered TaskListener.");
            }
            upgradeTask = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(TASK_CANCELLING_KEY, taskIsCancelling);
    }

    @Override
    public void processTaskUpdate(int[]... vals) {
        int progress = vals[0][0];
        uiController.updateProgressBar(progress);

        /*
        if (phase == ResourceEngineTask.PHASE_DOWNLOAD) {
            updateProgress(Localization.get("updates.found", new String[] {""+done,""+total}), DIALOG_INSTALL_PROGRESS);
        } else if (phase == ResourceEngineTask.PHASE_COMMIT) {
            updateProgress(Localization.get("updates.downloaded"), DIALOG_INSTALL_PROGRESS);
        }
        */
    }

    @Override
    public void processTaskResult(ResourceEngineOutcomes result) {
        if (result == ResourceEngineOutcomes.StatusInstalled) {
            uiController.setUnappliedInstallButtonState();
        } else {
            uiController.setIdleButtonState();
        }

        unregisterTask();

        boolean startOverInstall = false;
        switch (result) {
            case StatusInstalled:
            case StatusUpToDate:
            case StatusMissingDetails:
            case StatusMissing:
            case StatusBadReqs:
                break;
            case StatusFailState:
                startOverInstall = true;
                break;
            case StatusNoLocalStorage:
                startOverInstall = true;
                break;
            case StatusBadCertificate:
                break;
            case StatusDuplicateApp:
                startOverInstall = true;
                break;
            default:
                startOverInstall = true;
                break;
         }

        // Did the install fail in a way where the existing
        // resource table should be reused in the next install
        // attempt?
        CommCareApp app = CommCareApplication._().getCurrentApp();
        app.getAppPreferences().edit().putBoolean(UpgradeTask.KEY_START_OVER, startOverInstall).commit();
        // Check if we want to record this as a 'last install
        // time', based on the state of the resource table before
        // and after this install took place
        ResourceTable temporary = app.getCommCarePlatform().getUpgradeResourceTable();

        if (temporary.getTableReadiness() == ResourceTable.RESOURCE_TABLE_PARTIAL &&
                resourceTableWasFresh) {
            app.getAppPreferences().edit().putLong(CommCareSetupActivity.KEY_LAST_INSTALL,
                    System.currentTimeMillis()).commit();
        }
    }

    @Override
    public void processTaskCancel(ResourceEngineOutcomes result) {
        unregisterTask();

        uiController.setIdleButtonState();
        uiController.updateProgressBar(0);
    }

    protected void startUpgradeCheck() {
        try {
            upgradeTask = UpgradeTask.getNewInstance();
            upgradeTask.registerTaskListener(this);
        } catch (IllegalStateException e) {
            enterErrorState("There is already an existing upgrade task instance.");
            return;
        } catch (TaskListenerException e) {
            enterErrorState("Attempting to register a TaskListener to an " +
                    "already registered task.");
            return;
        }

        // store what the state of the resource table was before this
        // install, so we can compare it to the state after and decide if
        // this should count as a 'last install time'
        CommCareApp app = CommCareApplication._().getCurrentApp();
        int tableStateBeforeInstall =
            app.getCommCarePlatform().getUpgradeResourceTable().getTableReadiness();

        resourceTableWasFresh =
            (tableStateBeforeInstall == ResourceTable.RESOURCE_TABLE_EMPTY) ||
            (tableStateBeforeInstall == ResourceTable.RESOURCE_TABLE_INSTALLED);

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
}
