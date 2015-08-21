package org.commcare.dalvik.activities;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.tasks.InstallStagedUpdateTask;
import org.commcare.android.tasks.ResourceEngineOutcomes;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.TaskListener;
import org.commcare.android.tasks.TaskListenerException;
import org.commcare.android.tasks.UpdateTask;
import org.commcare.android.util.InstallAndUpdateUtils;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.utils.ConnectivityStatus;
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
        implements TaskListener<Integer, ResourceEngineOutcomes> {

    private static final String TAG = UpdateActivity.class.getSimpleName();
    private static final String TASK_CANCELLING_KEY = "update_task_cancelling";

    private boolean taskIsCancelling;

    private UpdateTask updateTask;

    private UpdateUiController uiController;

    private static final int DIALOG_UPGRADE_INSTALL = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiController = new UpdateUiController(this);

        loadSaveInstanceState(savedInstanceState);

        setupUpdateTask();
    }

    private void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            taskIsCancelling =
                    savedInstanceState.getBoolean(TASK_CANCELLING_KEY, false);
        }
    }

    private void setupUpdateTask() {
        updateTask = UpdateTask.getRunningInstance();

        try {
            if (updateTask != null) {
                updateTask.registerTaskListener(this);
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
        if (updateTask != null) {
            currentProgress = updateTask.getProgress();
            maxProgress = updateTask.getMaxProgress();
            if (taskIsCancelling) {
                uiController.cancelling();
            } else {
                setUiStateFromRunningTask(updateTask.getStatus());
            }
        } else {
            pendingUpdateOrIdle();
        }
        uiController.updateProgressBar(currentProgress, maxProgress);
        uiController.refreshStatusText();
    }

    private void setUiStateFromRunningTask(AsyncTask.Status taskStatus) {
        switch (taskStatus) {
            case RUNNING:
                uiController.downloading();
                break;
            case PENDING:
                pendingUpdateOrIdle();
                break;
            case FINISHED:
                uiController.error();
                break;
            default:
                uiController.error();
        }
    }

    private void pendingUpdateOrIdle() {
        if (InstallAndUpdateUtils.isUpdateInstallReady()) {
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
        if (updateTask != null) {
            try {
                updateTask.unregisterTaskListener(this);
            } catch (TaskListenerException e) {
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
            uiController.upToDate();
        }

        unregisterTask();

        uiController.refreshStatusText();
    }

    @Override
    public void processTaskCancel(ResourceEngineOutcomes result) {
        unregisterTask();

        uiController.idle();
    }

    protected void startUpdateCheck() {
        try {
            updateTask = UpdateTask.getNewInstance();
            updateTask.registerTaskListener(this);
        } catch (IllegalStateException e) {
            enterErrorState("There is already an existing update task instance.");
            return;
        } catch (TaskListenerException e) {
            enterErrorState("Attempting to register a TaskListener to an " +
                    "already registered task.");
            return;
        }

        // TODO PLM: is this the correct way to get the ref?
        CommCareApp app = CommCareApplication._().getCurrentApp();
        SharedPreferences prefs = app.getAppPreferences();
        String ref = prefs.getString(ResourceEngineTask.DEFAULT_APP_SERVER, null);
        updateTask.execute(ref);
        uiController.downloading();
    }

    private void enterErrorState(String errorMsg) {
        Log.e(TAG, errorMsg);
        uiController.error();
    }

    public void stopUpdateCheck() {
        if (updateTask != null) {
            updateTask.cancel(true);
            taskIsCancelling = true;
            uiController.cancelling();
        } else {
            uiController.idle();
        }
    }

    protected void lauchUpdateInstallTask() {
        InstallStagedUpdateTask<UpdateActivity> task =
                new InstallStagedUpdateTask<UpdateActivity>(DIALOG_UPGRADE_INSTALL) {

                    @Override
                    protected void deliverResult(UpdateActivity receiver,
                                                 ResourceEngineOutcomes result) {
                        if (result == ResourceEngineOutcomes.StatusInstalled) {
                            uiController.updateInstalled();
                        } else {
                            uiController.error();
                        }
                    }

                    @Override
                    protected void deliverUpdate(UpdateActivity receiver,
                                                 int[]... update) {
                    }

                    @Override
                    protected void deliverError(UpdateActivity receiver,
                                                Exception e) {
                        uiController.updateInstalled();
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
        CustomProgressDialog dialog =
            CustomProgressDialog.newInstance(title, message, taskId);
        dialog.setCancelable(false);
        return dialog;
    }
}
