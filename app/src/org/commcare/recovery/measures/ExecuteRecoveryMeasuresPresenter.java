package org.commcare.recovery.measures;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.activities.InstallArchiveActivity;
import org.commcare.activities.PromptActivity;
import org.commcare.activities.PromptApkUpdateActivity;
import org.commcare.activities.PromptCCReinstallActivity;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.resources.model.Resource;
import org.commcare.tasks.ResourceEngineTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.TaskListener;
import org.commcare.util.LogTypes;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.List;

import static org.commcare.engine.resource.ResourceInstallUtils.getProfileReference;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_APP_REINSTALL;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_APP_UPDATE;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_CC_REINSTALL_NEEDED;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_CC_UPDATE_NEEDED;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_CLEAR_USER_DATA;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_EXECUTED;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_FAILED;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_TOO_SOON;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_WAITING;

public class ExecuteRecoveryMeasuresPresenter {


    private final ExecuteRecoveryMeasuresActivity mActivity;
    private RecoveryMeasure mCurrentMeasure;
    private @RecoveryMeasure.RecoveryMeasureStatus
    int mLastExecutionStatus;

    private static final int REINSTALL_TASK_ID = 1;
    public static final int OFFLINE_INSTALL_REQUEST = 1001;

    public ExecuteRecoveryMeasuresPresenter(ExecuteRecoveryMeasuresActivity activity) {
        mActivity = activity;
    }

    void executePendingMeasures() {
        SqlStorage<RecoveryMeasure> storage =
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        List<RecoveryMeasure> toExecute = RecoveryMeasuresHelper.getPendingRecoveryMeasuresInOrder(storage);
        if (toExecute.size() == 0) {
            mActivity.runFinish();
            return;
        }

        List<Integer> executed = new ArrayList<>();
        for (RecoveryMeasure measure : toExecute) {
            mCurrentMeasure = measure;
            mLastExecutionStatus = executeMeasure(measure);
            if (mLastExecutionStatus == STATUS_EXECUTED) {
                HiddenPreferences.setLatestRecoveryMeasureExecuted(measure.getSequenceNumber());
                executed.add(measure.getID());
            } else {
                // Either it's too soon to retry, the execution failed, or we're waiting for an
                // async process to finish
                if (mLastExecutionStatus != STATUS_TOO_SOON) {
                    measure.setLastAttemptTime(storage);
                }
                break;
            }
        }

        for (Integer id : executed) {
            storage.remove(id);
        }

        if (mLastExecutionStatus != STATUS_WAITING) {
            mActivity.runFinish();
        }
    }

    public @RecoveryMeasure.RecoveryMeasureStatus
    int executeMeasure(RecoveryMeasure measure) {
        if (measure.triedTooRecently()) {
            return STATUS_TOO_SOON;
        }
        // All recovery measures assume there is a seated app to execute upon, so check that first
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp == null) {
            return STATUS_FAILED;
        }

        try {
            switch (measure.getType()) {
                case MEASURE_TYPE_APP_REINSTALL:
                    showInstallMethodChooser();
                    return STATUS_WAITING;
                case MEASURE_TYPE_APP_UPDATE:
                    return executeAutoUpdate();
                case MEASURE_TYPE_CLEAR_USER_DATA:
                    clearDataForCurrentOrLastUser();
                    return STATUS_EXECUTED;
                case MEASURE_TYPE_CC_REINSTALL_NEEDED:
                    launchActivity(PromptCCReinstallActivity.class, ExecuteRecoveryMeasuresActivity.PROMPT_APK_REINSTALL);
                    return STATUS_WAITING;
                case MEASURE_TYPE_CC_UPDATE_NEEDED:
                    launchActivity(PromptApkUpdateActivity.class, ExecuteRecoveryMeasuresActivity.PROMPT_APK_UPDATE);
                    return STATUS_WAITING;
                default:
                    // A type that we do not recognize, mark it as executed so that it gets ignored
                    return STATUS_EXECUTED;
            }
        } catch (Exception e) {
            // If anything goes wrong in the recovery measure execution, just count that as a failure
            mActivity.displayError("Failed to execute ");
            Logger.exception(String.format("Encountered exception while executing recovery measure of type %s", measure.getType()), e);
        }
        return STATUS_FAILED;
    }

    private void reinstallApp(CommCareApp currentApp, String profileRef, int authority) {
        ResourceEngineTask<ExecuteRecoveryMeasuresActivity> task
                = new sResourceEngineTask<ExecuteRecoveryMeasuresActivity>(
                currentApp,
                REINSTALL_TASK_ID,
                false, authority,
                true);
        task.connect(mActivity);
        task.execute(profileRef);
    }

    private void launchActivity(Class activity, int requestCode) {
        Intent i = new Intent(mActivity, activity);
        i.putExtra(PromptActivity.FROM_RECOVERY_MEASURE, true);
        mActivity.startActivityForResult(i, requestCode);
    }

    private @RecoveryMeasure.RecoveryMeasureStatus
    int executeAutoUpdate() {
        CommCareApplication.startAutoUpdate(mActivity, true, new TaskListener<Integer, ResultAndError<AppInstallStatus>>() {
            @Override
            public void handleTaskUpdate(Integer... updateVals) {
                int progress = updateVals[0];
                int max = updateVals[1];
                String msg = Localization.get("updates.found",
                        new String[]{"" + progress, "" + max});
                mActivity.updateStatus(msg);
            }

            @Override
            public void handleTaskCompletion(ResultAndError<AppInstallStatus> appInstallStatusResultAndError) {
                AppInstallStatus result = appInstallStatusResultAndError.data;
                if (result == AppInstallStatus.UpdateStaged || result == AppInstallStatus.UpToDate) {
                    onAsyncExecutionSuccess("App update");
                } else {
                    onAsyncExecutionFailure("App update", appInstallStatusResultAndError.data.name());
                }
                mActivity.hideProgress();
            }

            @Override
            public void handleTaskCancellation() {
                onAsyncExecutionFailure("App update", "update task cancelled");
                mActivity.hideProgress();
            }
        });
        return STATUS_WAITING;
    }

    private static void clearDataForCurrentOrLastUser() {
        try {
            CommCareApplication.instance().getSession();
            AppUtils.clearUserData();
        } catch (SessionUnavailableException e) {
            String lastUser = CommCareApplication.instance().getCurrentApp().getAppPreferences().
                    getString(HiddenPreferences.LAST_LOGGED_IN_USER, null);
            if (lastUser != null) {
                AppUtils.wipeSandboxForUser(lastUser);
            }
        }
    }

    private void markMeasureAsExecuted() {
        HiddenPreferences.setLatestRecoveryMeasureExecuted(mCurrentMeasure.getSequenceNumber());
        getStorage().remove(mCurrentMeasure);
    }

    public void onAsyncExecutionSuccess(String action) {
        mLastExecutionStatus = STATUS_EXECUTED;
        markMeasureAsExecuted();
        // so that we pick back up with the next measure, if there are any more
        executePendingMeasures();
    }

    public void onAsyncExecutionFailure(String action, String reason) {
        mLastExecutionStatus = STATUS_FAILED;
        Logger.log(LogTypes.TYPE_MAINTENANCE, String.format(
                "%s failed with %s for recovery measure %s", action, reason, mCurrentMeasure.getSequenceNumber()));
        mActivity.runFinish();
    }

    public RecoveryMeasure getCurrentMeasure() {
        return mCurrentMeasure;
    }

    public void setMeasureFromId(int measureId) {
        mCurrentMeasure = getStorage().read(measureId);
    }

    public void appInstallExecutionFailed(String reason) {
        onAsyncExecutionFailure("App install", reason);
    }

    private static SqlStorage<RecoveryMeasure> getStorage() {
        return CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
    }


    public void showInstallMethodChooser() {
        String title = "Install Method";
        String message = "Choose installation method";
        StandardAlertDialog d = new StandardAlertDialog(mActivity, title, message);
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            mActivity.dismissAlertDialog();
            if (which == AlertDialog.BUTTON_POSITIVE) {
                doOnlineAppInstall();
            } else if (which == AlertDialog.BUTTON_NEGATIVE) {
                showOfflineInstallActivity();
            }
        };
        d.setPositiveButton("Online", listener);
        d.setNegativeButton("Offline(Using CCZ)", listener);
        mActivity.showAlertDialog(d);
    }

    public void doOnlineAppInstall() {
        reinstallApp(CommCareApplication.instance().getCurrentApp(),
                getProfileReference(),
                Resource.RESOURCE_AUTHORITY_REMOTE);
    }

    public void showOfflineInstallActivity() {
        Intent i = new Intent(mActivity, InstallArchiveActivity.class);
        mActivity.startActivityForResult(i, OFFLINE_INSTALL_REQUEST);
    }

    public void doOfflineAppInstall(String profileRef) {
        reinstallApp(CommCareApplication.instance().getCurrentApp(),
                profileRef,
                Resource.RESOURCE_AUTHORITY_LOCAL);
    }

    private static class sResourceEngineTask<T> extends ResourceEngineTask<ExecuteRecoveryMeasuresActivity> {
        public sResourceEngineTask(CommCareApp currentApp, int taskId, boolean shouldSleep, int authority, boolean reinstall) {
            super(currentApp, taskId, shouldSleep, authority, reinstall);
        }

        @Override
        protected void deliverResult(ExecuteRecoveryMeasuresActivity receiver, AppInstallStatus result) {
            CommCareSetupActivity.handleAppInstallResult(this, receiver, result);
        }

        @Override
        protected void deliverUpdate(ExecuteRecoveryMeasuresActivity receiver,
                                     int[]... update) {
            receiver.updateResourceProgress(update[0][0], update[0][1], update[0][2]);
        }

        @Override
        protected void deliverError(ExecuteRecoveryMeasuresActivity receiver,
                                    Exception e) {
            receiver.failUnknown(AppInstallStatus.UnknownFailure);
        }
    }
}
