package org.commcare.recovery.measures;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import org.commcare.AppUtils;
import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.AppManagerActivity;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.activities.InstallArchiveActivity;
import org.commcare.activities.PromptActivity;
import org.commcare.activities.PromptApkUpdateActivity;
import org.commcare.activities.PromptCCReinstallActivity;
import org.commcare.activities.UpdateActivity;
import org.commcare.dalvik.R;
import org.commcare.engine.references.ArchiveFileRoot;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.interfaces.BasePresenterContract;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.resources.model.Resource;
import org.commcare.tasks.InstallStagedUpdateTask;
import org.commcare.tasks.ResourceEngineTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.TaskListener;
import org.commcare.tasks.TaskListenerRegistrationException;
import org.commcare.tasks.UpdateTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.CczUtils;
import org.commcare.utils.FileUtil;
import org.commcare.utils.StringUtils;
import org.commcare.utils.ZipUtils;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.util.List;
import java.util.NoSuchElementException;

import static org.commcare.engine.resource.ResourceInstallUtils.getProfileReference;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_APP_OFFLINE_REINSTALL_AND_UPDATE;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_APP_REINSTALL_AND_UPDATE;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_APP_UPDATE;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_CC_REINSTALL;
import static org.commcare.recovery.measures.RecoveryMeasure.MEASURE_TYPE_CC_UPDATE;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_EXECUTED;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_FAILED;
import static org.commcare.recovery.measures.RecoveryMeasure.STATUS_WAITING;

public class ExecuteRecoveryMeasuresPresenter implements BasePresenterContract, TaskListener<Integer, ResultAndError<AppInstallStatus>> {


    private final ExecuteRecoveryMeasuresActivity mActivity;
    private RecoveryMeasure mCurrentMeasure;
    private UpdateTask updateTask;
    private @RecoveryMeasure.RecoveryMeasureStatus
    int mLastExecutionStatus;
    private String mLastDisplayStatus;

    private String mTargetPath;
    private String mAppArchivePath;
    private boolean cczSelectionEnabled;

    private static final int REINSTALL_TASK_ID = 1;
    private static final int INSTALL_UPGRADE_TASK_ID = 11;

    static final int OFFLINE_INSTALL_REQUEST = 1001;
    static final int REQUEST_CCZ = 2001;

    private static final String TAG = ExecuteRecoveryMeasuresPresenter.class.getSimpleName();

    private static final String CURRENT_MEASUERE_ID_KEY = "current_measure_id";
    private static final String LAST_STATUS_KEY = "last_status";
    private static final String LAST_DISPLAY_STATUS_KEY = "last_display_status";
    private static final String ARCHIVE_PATH_KEY = "archive_path";
    private static final String CCZ_SELECTION_ENABLED_KEY = "ccz_selection_enabled";

    ExecuteRecoveryMeasuresPresenter(ExecuteRecoveryMeasuresActivity activity) {
        mActivity = activity;
    }

    @Override
    public void start() {
        try {
            if (cczSelectionEnabled) {
                setCczSelectionVisibility(true);
            }

            if (mLastExecutionStatus == STATUS_FAILED) {
                mActivity.enableRetry();
                updateStatus(mLastDisplayStatus);
            } else if (!(connectToUpdateTask() || mActivity.aTaskInProgress())) {
                // If update task in progress, connect to it and do nothing
                // or if any other task in progress, do nothing and let activity connect to it through stateholder as usual.
                // Otherwise execute any pending measures.
                executePendingMeasures();
            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    // recursively executes measures one after another
    private void executePendingMeasures() {
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
        // All recovery measures assume there is a seated app to execute upon, so check that first
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        if (currentApp == null) {
            return STATUS_FAILED;
        }


        switch (mCurrentMeasure.getType()) {
            case MEASURE_TYPE_APP_REINSTALL_AND_UPDATE:
                if (AppUtils.notOnLatestAppVersion()) {
                    showInstallMethodChooser();
                    return STATUS_WAITING;
                } else {
                    return STATUS_EXECUTED;
                }
            case MEASURE_TYPE_APP_OFFLINE_REINSTALL_AND_UPDATE:
                if (AppUtils.notOnLatestAppVersion()) {
                    initateAutoCczScan();
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
            case MEASURE_TYPE_CC_REINSTALL:
                if (AppUtils.notOnLatestCCVersion()) {
                    launchActivity(PromptCCReinstallActivity.class, ExecuteRecoveryMeasuresActivity.PROMPT_APK_REINSTALL);
                    return STATUS_WAITING;
                } else {
                    return STATUS_EXECUTED;
                }
            case MEASURE_TYPE_CC_UPDATE:
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
    }

    private void handleException(Exception e) {
        // If anything goes wrong in the recovery measure execution, just count that as a failure
        updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_faiure));
        Logger.exception(String.format("Encountered exception while executing recovery measure of type %s",
                mCurrentMeasure != null ? mCurrentMeasure.getType() : "unknown"), e);
    }

    private void initateAutoCczScan() {
        ScanCczTask scanCczTask = new ScanCczTask();
        scanCczTask.connect(mActivity);
        scanCczTask.executeParallel();
        updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_ccz_scan_in_progress));
    }

    private void reinstallApp(String profileRef, int authority) {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        ResourceEngineTask<ExecuteRecoveryMeasuresActivity> task
                = new sResourceEngineTask(
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

    private void executeAutoUpdate() {
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

    private boolean connectToUpdateTask() {
        updateTask = UpdateTask.getRunningInstance();
        if (updateTask != null) {
            try {
                updateTask.registerTaskListener(this);
            } catch (TaskListenerRegistrationException e) {
                Log.e(TAG, "Attempting to register a TaskListener to an already " +
                        "registered task.");
            }
            return true;
        }
        return false;
    }

    private void updateStatus(String status) {
        mLastDisplayStatus = status;
        if (mActivity != null) {
            mActivity.updateStatus(status);
        }
    }

    private void markMeasureAsExecuted() {
        HiddenPreferences.setLatestRecoveryMeasureExecuted(mCurrentMeasure.getSequenceNumber());
        getStorage().remove(mCurrentMeasure);
    }

    private void onAsyncExecutionSuccess() {
        try {
            mLastExecutionStatus = STATUS_EXECUTED;
            markMeasureAsExecuted();
            // so that we pick back up with the next measure, if there are any more
            executePendingMeasures();
        } catch (Exception e) {
            handleException(e);
        }
    }

    private void onAsyncExecutionFailure(String reason) {
        try {
            mLastExecutionStatus = STATUS_FAILED;
            Logger.log(LogTypes.TYPE_MAINTENANCE, String.format(
                    "%s failed with %s for recovery measure %s", mCurrentMeasure.getType(), reason, mCurrentMeasure.getSequenceNumber()));
            if (mActivity != null) {
                mActivity.disableLoadingIndicator();
                mActivity.enableRetry();
            }
        } catch (Exception e) {
            handleException(e);
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
        reinstallApp(getProfileReference(), Resource.RESOURCE_AUTHORITY_REMOTE);
    }

    private void showOfflineInstallActivity() {
        Intent i = new Intent(mActivity, InstallArchiveActivity.class);
        mActivity.startActivityForResult(i, OFFLINE_INSTALL_REQUEST);
    }

    public void doOfflineAppInstall(String profileRef) {
        reinstallApp(profileRef, Resource.RESOURCE_AUTHORITY_LOCAL);
    }

    public boolean shouldAllowBackPress() {
        return false;
    }

    public void onAppReinstallSuccess() {
        if (mCurrentMeasure.getType().contentEquals(MEASURE_TYPE_APP_REINSTALL_AND_UPDATE)
                || mCurrentMeasure.getType().contentEquals(MEASURE_TYPE_APP_OFFLINE_REINSTALL_AND_UPDATE)) {
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

    @Override
    public void saveInstanceState(Bundle out) {
        out.putInt(CURRENT_MEASUERE_ID_KEY, mCurrentMeasure != null ? mCurrentMeasure.getID() : -1);
        out.putInt(LAST_STATUS_KEY, mLastExecutionStatus);
        out.putString(LAST_DISPLAY_STATUS_KEY, mLastDisplayStatus);
        out.putString(ARCHIVE_PATH_KEY, mAppArchivePath);
        out.putBoolean(CCZ_SELECTION_ENABLED_KEY, cczSelectionEnabled);
    }

    @Override
    public void loadSaveInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            setMeasureFromId(savedInstanceState.getInt(CURRENT_MEASUERE_ID_KEY));
            mLastExecutionStatus = savedInstanceState.getInt(LAST_STATUS_KEY);
            mLastDisplayStatus = savedInstanceState.getString(LAST_DISPLAY_STATUS_KEY);
            mAppArchivePath = savedInstanceState.getString(ARCHIVE_PATH_KEY);
            cczSelectionEnabled = savedInstanceState.getBoolean(CCZ_SELECTION_ENABLED_KEY, false);
        }
    }

    private void setMeasureFromId(int measureId) {
        if (measureId != -1) {
            try {
                mCurrentMeasure = getStorage().read(measureId);
            } catch (NoSuchElementException e) {
                mCurrentMeasure = null;
            }
        }
    }

    @Override
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

    public void updateCcz(File archive) {
        mAppArchivePath = archive.getAbsolutePath();
        mActivity.updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_ccz_found_scan_in_progress));
        mActivity.showReinstall();
    }

    private void unZipCcz(String filePath) {
        mTargetPath = CczUtils.getCczTargetPath();
        ZipUtils.UnzipFile(mActivity, filePath, mTargetPath);
        mActivity.enableLoadingIndicator();
        setCczSelectionVisibility(false);
    }


    public void selectCczFromFileSystem() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        try {
            mActivity.startActivityForResult(intent, REQUEST_CCZ);
        } catch (ActivityNotFoundException e) {
            updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_select_ccz_no_file_browser));
        }
    }

    public void updateCczFromIntent(Intent intent) {
        String filePath = FileUtil.getFileLocationFromIntent(intent);
        if (filePath != null) {
            mAppArchivePath = filePath;
            unZipCcz(filePath);
        } else {
            updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_invalid_ccz_path));
        }
    }

    public void reinstallFromScannedCcz() {
        unZipCcz(mAppArchivePath);
    }

    public void retry() {
        clearState();
        executePendingMeasures();
    }

    private void clearState() {
        mAppArchivePath = null;
        mCurrentMeasure = null;
        mLastDisplayStatus = null;
        mLastExecutionStatus = -1;
        cczSelectionEnabled = false;
    }

    public void launchAppManager() {
        Intent i = new Intent(mActivity, AppManagerActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivity.startActivity(i);
    }

    // CCZ Scan callbacks
    public void onCCZScanComplete() {
        mActivity.hideReinstall();
        if (mAppArchivePath != null) {
            unZipCcz(mAppArchivePath);
        } else {
            // no ccz found, allow user to manually locate ccz or retry
            updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_no_ccz_found));
            setCczSelectionVisibility(true);
            mActivity.enableRetry();
            mActivity.disableLoadingIndicator();
        }
    }

    private void setCczSelectionVisibility(boolean isEnable) {
        cczSelectionEnabled = isEnable;
        mActivity.setCczSelectionVisibility(isEnable);
    }


    public void onCczScanFailed(Exception e) {
        updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_ccz_scan_failed));
        onAsyncExecutionFailure(e.getMessage());
        setCczSelectionVisibility(true);
    }

    private void installPendingUpdate() {
        InstallStagedUpdateTask<ExecuteRecoveryMeasuresActivity> task = new sInstallUpdateTask(INSTALL_UPGRADE_TASK_ID);
        task.connect(mActivity);
        task.executeParallel();
    }

    public void OnUpdateInstalled() {
        UpdateActivity.OnSuccessfulUpdate(true, false);
        onAsyncExecutionSuccess();
    }

    public void OnUpdateInstallFailed(Exception e) {
        onAsyncExecutionFailure(e.getMessage());
    }

    // UnZip Callbacks
    public void onUnzipSuccessful() {
        ArchiveFileRoot afr = CommCareApplication.instance().getArchiveFileRoot();
        String mGUID = afr.addArchiveFile(mTargetPath);
        String ref = "jr://archive/" + mGUID + "/profile.ccpr";
        doOfflineAppInstall(ref);
    }

    public void updateUnZipProgress(String update) {
        mActivity.updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_unzip_progress, update));
    }

    public void onUnzipFailure(String cause) {
        updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_unzip_error));
        onAsyncExecutionFailure(cause);
        setCczSelectionVisibility(true);
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
            updateStatus(msg);
        }
    }

    @Override
    public void handleTaskCompletion(ResultAndError<AppInstallStatus> appInstallStatusResultAndError) {
        AppInstallStatus result = appInstallStatusResultAndError.data;
        if (result == AppInstallStatus.UpdateStaged || result == AppInstallStatus.UpToDate) {
            installPendingUpdate();
        } else {
            updateStatus(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_known_error, appInstallStatusResultAndError.errorMessage));
            onAsyncExecutionFailure(appInstallStatusResultAndError.data.name());
        }
    }

    @Override
    public void handleTaskCancellation() {
        onAsyncExecutionFailure(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_update_cancelled));
    }


    public void OnOfflineInstallCancelled() {
        onAsyncExecutionFailure(StringUtils.getStringRobust(mActivity, R.string.recovery_measure_offline_install_cancelled));
    }


    static class sInstallUpdateTask extends InstallStagedUpdateTask<ExecuteRecoveryMeasuresActivity> {

        public sInstallUpdateTask(int taskId) {
            super(taskId);
        }

        @Override
        protected void deliverResult(ExecuteRecoveryMeasuresActivity activity, AppInstallStatus appInstallStatus) {
            activity.handleInstallUpdateResult(appInstallStatus);
        }

        @Override
        protected void deliverUpdate(ExecuteRecoveryMeasuresActivity activity, int[]... update) {
        }

        @Override
        protected void deliverError(ExecuteRecoveryMeasuresActivity activity, Exception e) {
            activity.handleInstallUpdateFailure(e);
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
