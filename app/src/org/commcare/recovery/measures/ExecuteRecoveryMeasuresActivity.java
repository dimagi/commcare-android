package org.commcare.recovery.measures;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.CommCareApplication;
import org.commcare.activities.BlockingProcessActivity;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.TaskListener;
import org.commcare.utils.StorageUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by amstone326 on 5/22/18.
 */
public class ExecuteRecoveryMeasuresActivity extends BlockingProcessActivity
        implements ResourceEngineListener, TaskListener<Integer, ResultAndError<AppInstallStatus>> {

    protected static final int PROMPT_APK_UPDATE = 1;
    protected static final int PROMPT_APK_REINSTALL = 2;

    private static final String CURRENTLY_EXECUTING_ID = "currently-executing-id";
    private static final String CURRENTLY_EXECUTING_SEQUENCE_NUM = "currently-executing-sequence-num";
    private static final String LAST_EXECUTION_STATUS = "last-execution-status";

    private int idOfMeasureCurrentlyExecuting;
    private long sequenceNumOfMeasureCurrentlyExecuting;
    private int lastExecutionStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            idOfMeasureCurrentlyExecuting = savedInstanceState.getInt(CURRENTLY_EXECUTING_ID);
            sequenceNumOfMeasureCurrentlyExecuting = savedInstanceState.getLong(CURRENTLY_EXECUTING_SEQUENCE_NUM);
            lastExecutionStatus = savedInstanceState.getInt(LAST_EXECUTION_STATUS);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(CURRENTLY_EXECUTING_ID, idOfMeasureCurrentlyExecuting);
        out.putLong(CURRENTLY_EXECUTING_SEQUENCE_NUM, sequenceNumOfMeasureCurrentlyExecuting);
        out.putInt(LAST_EXECUTION_STATUS, lastExecutionStatus);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case PROMPT_APK_UPDATE:
                onAsyncExecutionSuccess("CommCare update prompt");
                break;
            case PROMPT_APK_REINSTALL:
                onAsyncExecutionSuccess("CommCare reinstall prompt");
                break;
        }
    }

    @Override
    protected String getDisplayTextKey() {
        return "executing.recovery.measures";
    }

    @Override
    protected Runnable buildProcessToRun(ProcessFinishedHandler handler) {
        return () -> {
            executePendingMeasures();
            handler.sendEmptyMessage(0);
        };
    }

    @Override
    protected void setResultOnIntent(Intent i) {
        i.putExtra(RecoveryMeasuresManager.RECOVERY_MEASURES_LAST_STATUS, lastExecutionStatus);
    }

    void executePendingMeasures() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {

        }
        SqlStorage<RecoveryMeasure> storage =
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        List<Integer> executed = new ArrayList<>();
        for (RecoveryMeasure measure : StorageUtils.getPendingRecoveryMeasuresInOrder(storage)) {
            idOfMeasureCurrentlyExecuting = measure.getID();
            sequenceNumOfMeasureCurrentlyExecuting = measure.getSequenceNumber();
            lastExecutionStatus = measure.execute(this);
            if (lastExecutionStatus == RecoveryMeasure.STATUS_EXECUTED) {
                HiddenPreferences.setLatestRecoveryMeasureExecuted(measure.getSequenceNumber());
                executed.add(measure.getID());
            } else {
                // Either it's too soon to retry, the execution failed, or we're waiting for an
                // async process to finish
                if (lastExecutionStatus != RecoveryMeasure.STATUS_TOO_SOON) {
                    measure.setLastAttemptTime(storage);
                }
                break;
            }
        }

        for (Integer id : executed) {
            storage.remove(id);
        }
    }

    private void onAsyncExecutionSuccess(String action) {
        System.out.println(String.format(
                "%s succeeded for recovery measure %s", action, sequenceNumOfMeasureCurrentlyExecuting));
        lastExecutionStatus = RecoveryMeasure.STATUS_EXECUTED;

        SqlStorage<RecoveryMeasure> storage =
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        RecoveryMeasure justSucceeded = storage.read(this.idOfMeasureCurrentlyExecuting);
        HiddenPreferences.setLatestRecoveryMeasureExecuted(justSucceeded.getSequenceNumber());
        storage.remove(justSucceeded.getID());

        // so that we pick back up with the next measure, if there are any more
        executePendingMeasures();
    }

    private void appInstallExecutionFailed(String reason) {
        onAsyncExecutionFailure("App install", reason);
    }

    private void onAsyncExecutionFailure(String action, String reason) {
        lastExecutionStatus = RecoveryMeasure.STATUS_FAILED;
        System.out.println(String.format(
                "%s failed with %s for recovery measure %s", action, reason, sequenceNumOfMeasureCurrentlyExecuting));
    }

    // region - ResourceEngineListener method implementations

    @Override
    public void reportSuccess(boolean b) {
        onAsyncExecutionSuccess("App install");
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusmissing) {
        appInstallExecutionFailed("missing resource");
    }

    @Override
    public void failInvalidResource(InvalidResourceException e, AppInstallStatus statusmissing) {
        appInstallExecutionFailed("invalid resource");
    }

    @Override
    public void failBadReqs(String vReq, String vAvail, boolean majorIsProblem) {
        appInstallExecutionFailed("bad reqs");
    }

    @Override
    public void failUnknown(AppInstallStatus statusfailunknown) {
        appInstallExecutionFailed("unknown reason");
    }

    @Override
    public void updateResourceProgress(int done, int pending, int phase) {
    }

    @Override
    public void failWithNotification(AppInstallStatus statusfailstate) {
        appInstallExecutionFailed("notification");
    }

    @Override
    public void failTargetMismatch() {
        appInstallExecutionFailed("target mismatch");
    }

    // endregion

    // region - TaskListener method implementations

    @Override
    public void handleTaskUpdate(Integer... updateVals) {

    }

    @Override
    public void handleTaskCompletion(ResultAndError<AppInstallStatus> appInstallStatusResultAndError) {
        AppInstallStatus result = appInstallStatusResultAndError.data;
        if (result == AppInstallStatus.UpdateStaged || result == AppInstallStatus.UpToDate) {
            onAsyncExecutionSuccess("App update");
        } else {
            onAsyncExecutionFailure("App update", appInstallStatusResultAndError.data.name());
        }
    }

    @Override
    public void handleTaskCancellation() {
        onAsyncExecutionFailure("App update", "update task cancelled");
    }

    // endregion
}
