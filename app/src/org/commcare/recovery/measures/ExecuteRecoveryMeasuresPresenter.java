package org.commcare.recovery.measures;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.util.Log;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.activities.InstallArchiveActivity;
import org.commcare.activities.PromptActivity;
import org.commcare.activities.PromptApkUpdateActivity;
import org.commcare.activities.PromptCCReinstallActivity;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.resources.model.Resource;
import org.commcare.tasks.ResourceEngineTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.TaskListener;
import org.commcare.tasks.TaskListenerRegistrationException;
import org.commcare.tasks.UpdateTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.commcare.engine.resource.ResourceInstallUtils.getProfileReference;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_APP_REINSTALL_AND_UPDATE;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_APP_UPDATE;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_CC_REINSTALL_NEEDED;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_CC_UPDATE_NEEDED;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_CLEAR_USER_DATA;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_EXECUTED;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_FAILED;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_TOO_SOON;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_WAITING;

public class ExecuteRecoveryMeasuresPresenter implements TaskListener<Integer, ResultAndError<AppInstallStatus>> {


    private final ExecuteRecoveryMeasuresActivity mActivity;
    private RecoveryMeasure mCurrentMeasure;
    private UpdateTask updateTask;
    private @RecoveryMeasure.RecoveryMeasureStatus
    int mLastExecutionStatus;

    private static final int REINSTALL_TASK_ID = 1;
    static final int OFFLINE_INSTALL_REQUEST = 1001;


    public static final int RECOVERY_STATE_NONE = 0;
    public static final int RECOVERY_STATE_TASK_IN_PROGRESS = 1;
    public static final int RECOVERY_STATE_APP_UPDATE_IN_PROGRESS = 2;

    private static final String TAG = ExecuteRecoveryMeasuresPresenter.class.getSimpleName();

    @IntDef({RECOVERY_STATE_NONE, RECOVERY_STATE_TASK_IN_PROGRESS, RECOVERY_STATE_APP_UPDATE_IN_PROGRESS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RecoveryState {
    }

    @RecoveryState
    private int mRecoveryState = RECOVERY_STATE_NONE;

    ExecuteRecoveryMeasuresPresenter(ExecuteRecoveryMeasuresActivity activity) {
        mActivity = activity;
    }

    // recursively executes measures one after another
    void executePendingMeasures() {
        if (mRecoveryState == RECOVERY_STATE_NONE) {
            mActivity.enableLoadingIndicator();
            mCurrentMeasure = getNextMeasureToExecute();
            if (mCurrentMeasure != null) {
                mLastExecutionStatus = executeMeasure();
                if (mLastExecutionStatus == STATUS_EXECUTED) {
                    markMeasureAsExecuted();
                    executePendingMeasures();
                }
            } else {
                // Nothing to execute
                mActivity.runFinish();
            }
        } else if (mRecoveryState == RECOVERY_STATE_APP_UPDATE_IN_PROGRESS) {
            connectToUpdateTask();
        }
    }

    @Nullable
    private RecoveryMeasure getNextMeasureToExecute() {
        SqlStorage<RecoveryMeasure> storage =
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        List<RecoveryMeasure> toExecute = RecoveryMeasuresHelper.getPendingRecoveryMeasuresInOrder(storage);
        if (toExecute.size() != 0) {
            return toExecute.get(0);
        }
        return null;
    }

    @RecoveryMeasure.RecoveryMeasureStatus
    private int executeMeasure() {
//        if (mCurrentMeasure.triedTooRecently()) {
//            return STATUS_TOO_SOON;
//        }
        // All recovery measures assume there is a seated app to execute upon, so check that first
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp == null) {
            return STATUS_FAILED;
        }

        try {
            switch (mCurrentMeasure.getType()) {
                case MEASURE_TYPE_APP_REINSTALL_AND_UPDATE:
                    if (AppUtils.notOnLatestAppVersion()) {
                        showInstallMethodChooser();
                        return STATUS_WAITING;
                    } else {
                        return STATUS_EXECUTED;
                    }

                case MEASURE_TYPE_APP_UPDATE:
                    if (AppUtils.notOnLatestAppVersion()) {
                        executeAutoUpdate();
                        return STATUS_WAITING;
                    } else {
                        return STATUS_EXECUTED;
                    }
                case MEASURE_TYPE_CLEAR_USER_DATA:
                    clearDataForCurrentOrLastUser();
                    return STATUS_EXECUTED;
                case MEASURE_TYPE_CC_REINSTALL_NEEDED:
                    if (AppUtils.notOnLatestCCVersion()) {
                        launchActivity(PromptCCReinstallActivity.class, ExecuteRecoveryMeasuresActivity.PROMPT_APK_REINSTALL);
                        return STATUS_WAITING;
                    } else {
                        return STATUS_EXECUTED;
                    }
                case MEASURE_TYPE_CC_UPDATE_NEEDED:
                    if (AppUtils.notOnLatestCCVersion()) {
                        launchActivity(PromptApkUpdateActivity.class, ExecuteRecoveryMeasuresActivity.PROMPT_APK_UPDATE);
                        return STATUS_WAITING;
                    } else {
                        return STATUS_EXECUTED;
                    }
                default:
                    // A type that we do not recognize, mark it as executed so that it gets ignored
                    return STATUS_EXECUTED;
            }
        } catch (Exception e) {
            // If anything goes wrong in the recovery measure execution, just count that as a failure
            mActivity.displayError("Failed to execute ");
            Logger.exception(String.format("Encountered exception while executing recovery measure of type %s", mCurrentMeasure.getType()), e);
        }
        return STATUS_FAILED;
    }

    private void reinstallApp(CommCareApp currentApp, String profileRef, int authority) {
        ResourceEngineTask<ExecuteRecoveryMeasuresActivity> task
                = new sResourceEngineTask(
                currentApp,
                REINSTALL_TASK_ID,
                false, authority,
                true);
        task.connect(mActivity);
        task.execute(profileRef);
        mRecoveryState = RECOVERY_STATE_TASK_IN_PROGRESS;
    }

    private void launchActivity(Class activity, int requestCode) {
        Intent i = new Intent(mActivity, activity);
        i.putExtra(PromptActivity.FROM_RECOVERY_MEASURE, true);
        mActivity.startActivityForResult(i, requestCode);
    }

    private void executeAutoUpdate() {
        mRecoveryState = RECOVERY_STATE_APP_UPDATE_IN_PROGRESS;
        String ref = ResourceInstallUtils.getDefaultProfileRef();
        try {
            updateTask = UpdateTask.getNewInstance();
            updateTask.registerTaskListener(this);
            updateTask.executeParallel(ref);
        } catch (IllegalStateException e) {
            Log.w(TAG, "Trying to trigger auto-update when it is already running");
        } catch (TaskListenerRegistrationException e) {
            Logger.log(LogTypes.SOFT_ASSERT, "Attempting to register a TaskListener to an already registered update task.");
        }

    }

    private void connectToUpdateTask() {
        updateTask = UpdateTask.getRunningInstance();
        if (updateTask != null) {
            try {
                updateTask.registerTaskListener(this);
            } catch (TaskListenerRegistrationException e) {
                Log.e(TAG, "Attempting to register a TaskListener to an already " +
                        "registered task.");
            }
        }
    }

    private void updateStatus(String status) {
        if (mActivity != null) {
            mActivity.updateStatus(status);
        }
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

    private void onAsyncExecutionSuccess() {
        mLastExecutionStatus = STATUS_EXECUTED;
        mRecoveryState = RECOVERY_STATE_NONE;
        markMeasureAsExecuted();
        // so that we pick back up with the next measure, if there are any more
        executePendingMeasures();
    }

    private void onAsyncExecutionFailure(String reason) {
        mLastExecutionStatus = STATUS_FAILED;
        Logger.log(LogTypes.TYPE_MAINTENANCE, String.format(
                "%s failed with %s for recovery measure %s", mCurrentMeasure.getType(), reason, mCurrentMeasure.getSequenceNumber()));
        if (mActivity != null) {
            mActivity.disableLoadingIndicator();
            mActivity.enableRetry();
        }
    }

    public RecoveryMeasure getCurrentMeasure() {
        return mCurrentMeasure;
    }

    public void setMeasureFromId(int measureId) {
        try {
            mCurrentMeasure = getStorage().read(measureId);
        } catch (NoSuchElementException e) {
            mCurrentMeasure = null;
        }
    }

    public void appInstallExecutionFailed(AppInstallStatus status, String reason) {
        updateStatus(Localization.get(status.getLocaleKeyBase() + ".detail"));
        onAsyncExecutionFailure(reason);
    }

    private static SqlStorage<RecoveryMeasure> getStorage() {
        return CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
    }

    private void showInstallMethodChooser() {
        String title = StringUtils.getStringRobust(mActivity, R.string.recovery_measure_reinstall_method);
        String message = StringUtils.getStringRobust(mActivity, R.string.recovery_measure_reinstall_detail);
        StandardAlertDialog d = new StandardAlertDialog(mActivity, title, message);
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            mActivity.dismissAlertDialog();
            if (which == AlertDialog.BUTTON_POSITIVE) {
                doOnlineAppInstall();
            } else if (which == AlertDialog.BUTTON_NEGATIVE) {
                showOfflineInstallActivity();
            }
        };
        d.setPositiveButton(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_reinstall_online_method), listener);
        d.setNegativeButton(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_reinstall_offline_method), listener);
        mActivity.showAlertDialog(d);
    }

    private void doOnlineAppInstall() {
        reinstallApp(CommCareApplication.instance().getCurrentApp(),
                getProfileReference(),
                Resource.RESOURCE_AUTHORITY_REMOTE);
    }

    private void showOfflineInstallActivity() {
        Intent i = new Intent(mActivity, InstallArchiveActivity.class);
        mActivity.startActivityForResult(i, OFFLINE_INSTALL_REQUEST);
    }

    public void doOfflineAppInstall(String profileRef) {
        reinstallApp(CommCareApplication.instance().getCurrentApp(),
                profileRef,
                Resource.RESOURCE_AUTHORITY_LOCAL);
    }

    public boolean shouldAllowBackPress() {
        return false;
    }

    public void onAppReinstallSuccess() {
        if (mCurrentMeasure.getType().contentEquals(MEASURE_TYPE_APP_REINSTALL_AND_UPDATE)) {
            executeAutoUpdate();
        } else {
            onAsyncExecutionSuccess();
        }
    }

    public void onReturnFromPlaystorePrompts() {
        if (AppUtils.notOnLatestCCVersion()) {
            onAsyncExecutionFailure("App Not Updated");
        } else {
            onAsyncExecutionSuccess();
        }
    }

    public int getRecoveryState() {
        return mRecoveryState;
    }

    public void setRecoveryState(int recoveryState) {
        this.mRecoveryState = recoveryState;
    }


    // Update Task Listeners
    @Override
    public void handleTaskUpdate(Integer... updateVals) {
        if (mActivity != null) {
            int progress = updateVals[0];
            int max = updateVals[1];
            String msg = StringUtils.getStringRobust(
                    mActivity,
                    R.string.recovery_measure_app_update_progress,
                    new String[]{"" + progress, "" + max});
            mActivity.updateStatus(msg);
        }
    }

    @Override
    public void handleTaskCompletion(ResultAndError<AppInstallStatus> appInstallStatusResultAndError) {
        AppInstallStatus result = appInstallStatusResultAndError.data;
        if (result == AppInstallStatus.UpdateStaged || result == AppInstallStatus.UpToDate) {
            onAsyncExecutionSuccess();
            updateStatus("");
        } else {
            updateStatus(appInstallStatusResultAndError.errorMessage);
            onAsyncExecutionFailure(appInstallStatusResultAndError.data.name());
        }
    }

    @Override
    public void handleTaskCancellation() {
        onAsyncExecutionFailure("update task cancelled");
    }

    public void onActivityDestroy() {
        unregisterUpdate();
    }

    private void unregisterUpdate() {
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


    static class sResourceEngineTask extends ResourceEngineTask<ExecuteRecoveryMeasuresActivity> {
        sResourceEngineTask(CommCareApp currentApp, int taskId, boolean shouldSleep, int authority, boolean reinstall) {
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
