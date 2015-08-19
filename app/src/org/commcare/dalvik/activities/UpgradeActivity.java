package org.commcare.dalvik.activities;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.tasks.InstallStagedUpgradeTask;
import org.commcare.android.tasks.ResourceEngineOutcomes;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerException;
import org.commcare.android.tasks.UpgradeTask;
import org.commcare.android.util.InstallAndUpdateUtils;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.utils.ConnectivityStatus;
import org.javarosa.core.services.locale.Localization;

/**
 * Allow user to manage app upgrading:
 * - Check and download the latest upgrade
 * - Stop a downloading upgrade
 * - Apply a downloaded upgrade
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpgradeActivity extends CommCareActivity<UpgradeActivity>
        implements TaskListener<Integer, ResourceEngineOutcomes> {

    private static final String TAG = UpgradeActivity.class.getSimpleName();
    private static final String TASK_CANCELLING_KEY = "upgrade_task_cancelling";

    private boolean taskIsCancelling;
    private boolean resourceTableWasFresh;

    private UpgradeTask upgradeTask;

    private UpgradeUiController uiController;

    private static final int DIALOG_UPGRADE_INSTALL = 4;

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
            uiController.error();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (ConnectivityStatus.isNetworkNotConnected(this) &&
                ConnectivityStatus.isAirplaneModeOn(this)) {
            // TODO
            uiController.error();
            return;
        }

        int currentProgress = 0;
        int maxProgress = 0;
        if (upgradeTask != null) {
            currentProgress = upgradeTask.getProgress();
            maxProgress = upgradeTask.getMaxProgress();
            if (taskIsCancelling) {
                uiController.cancelling();
            } else {
                setUiStateFromRunningTask(upgradeTask.getStatus());
            }
        } else {
            pendingUpgradeOrIdle();
        }
        uiController.updateProgressBar(currentProgress, maxProgress);
        uiController.refreshStatusText();
    }

    protected void setUiStateFromRunningTask(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                uiController.downloading();
                break;
            case PENDING:
                pendingUpgradeOrIdle();
                break;
            case FINISHED:
                uiController.error();
                break;
            default:
                uiController.error();
        }
    }

    protected void pendingUpgradeOrIdle() {
        if (InstallAndUpdateUtils.isUpgradeInstallReady()) {
            uiController.unappliedUpdateAvailable();
        } else {
            uiController.idle();
        }
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
    public void processTaskUpdate(Integer... vals) {
        int progress = vals[0];
        int max = vals[1];
        uiController.updateProgressBar(progress, max);
        String msg = Localization.get("updates.found",
                new String[]{"" + progress, "" + max});
        uiController.updateProgressText(msg);
    }

    @Override
    public void processTaskResult(ResourceEngineOutcomes result) {
        if (result == ResourceEngineOutcomes.StatusUpdateStaged) {
            uiController.unappliedUpdateAvailable();
        } else {
            uiController.idle();
        }

        unregisterTask();

        boolean startOverInstall =
                (result == ResourceEngineOutcomes.StatusFailState ||
                        result == ResourceEngineOutcomes.StatusNoLocalStorage);

        // Did the install fail in a way where the existing
        // resource table should be reused in the next install
        // attempt?
        CommCareApp app = CommCareApplication._().getCurrentApp();
        app.getAppPreferences().edit().putBoolean(UpgradeTask.KEY_START_OVER, startOverInstall).commit();
        /*
        // Check if we want to record this as a 'last install
        // time', based on the state of the resource table before
        // and after this install took place
        ResourceTable temporary = app.getCommCarePlatform().getUpgradeResourceTable();

        if (temporary.getTableReadiness() == ResourceTable.RESOURCE_TABLE_PARTIAL &&
                resourceTableWasFresh) {
            app.getAppPreferences().edit().putLong(CommCareSetupActivity.KEY_LAST_INSTALL,
                    System.currentTimeMillis()).commit();
        }
        */
    }

    @Override
    public void processTaskCancel(ResourceEngineOutcomes result) {
        unregisterTask();

        uiController.idle();
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

        CommCareApp app = CommCareApplication._().getCurrentApp();
        /*
        // store what the state of the resource table was before this
        // install, so we can compare it to the state after and decide if
        // this should count as a 'last install time'
        int tableStateBeforeInstall =
            app.getCommCarePlatform().getUpgradeResourceTable().getTableReadiness();

        resourceTableWasFresh =
            (tableStateBeforeInstall == ResourceTable.RESOURCE_TABLE_EMPTY) ||
            (tableStateBeforeInstall == ResourceTable.RESOURCE_TABLE_INSTALLED);
        */

        SharedPreferences prefs = app.getAppPreferences();
        String ref = prefs.getString(ResourceEngineTask.DEFAULT_APP_SERVER, null);
        upgradeTask.execute(ref);
        uiController.downloading();
    }

    private void enterErrorState(String errorMsg) {
        Log.e(TAG, errorMsg);
        uiController.error();
    }

    public void stopUpgradeCheck() {
        if (upgradeTask != null) {
            upgradeTask.cancel(true);
            taskIsCancelling = true;
            uiController.cancelling();
        } else {
            uiController.idle();
        }
    }

    protected void launchUpgradeInstallTask() {
        InstallStagedUpgradeTask<UpgradeActivity> task =
                new InstallStagedUpgradeTask<UpgradeActivity>(UpgradeActivity.DIALOG_UPGRADE_INSTALL) {

                    @Override
                    protected void deliverResult(UpgradeActivity receiver,
                                                 ResourceEngineOutcomes result) {
                        if (result == ResourceEngineOutcomes.StatusInstalled) {
                            uiController.upgradeInstalled();
                        } else {
                            uiController.error();
                        }
                    }

                    @Override
                    protected void deliverUpdate(UpgradeActivity receiver,
                                                 int[]... update) {
                    }

                    @Override
                    protected void deliverError(UpgradeActivity receiver,
                                                Exception e) {
                        uiController.upgradeInstalled();
                    }
                };
        task.connect(this);
        task.execute();
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
        CustomProgressDialog dialog = CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        return dialog;
    }
}
