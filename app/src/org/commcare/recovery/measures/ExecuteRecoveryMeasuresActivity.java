package org.commcare.recovery.measures;

import android.os.Bundle;

import org.commcare.CommCareApplication;
import org.commcare.activities.BlockingProcessActivity;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.tasks.ResourceEngineListener;
import org.commcare.utils.StorageUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by amstone326 on 5/22/18.
 */
public class ExecuteRecoveryMeasuresActivity extends BlockingProcessActivity implements ResourceEngineListener {

    private static final String MEASURE_CURRENTLY_EXECUTING = "measure-currently-executing";

    private int idOfMeasureCurrentlyExecuting;
    private int sequenceNumOfMeasureCurrentlyExecuting;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            idOfMeasureCurrentlyExecuting = savedInstanceState.getInt(MEASURE_CURRENTLY_EXECUTING);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(MEASURE_CURRENTLY_EXECUTING, idOfMeasureCurrentlyExecuting);
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

    void executePendingMeasures() {
        SqlStorage<RecoveryMeasure> storage =
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        List<Integer> executed = new ArrayList<>();
        for (RecoveryMeasure measure : StorageUtils.getPendingRecoveryMeasuresInOrder(storage)) {
            idOfMeasureCurrentlyExecuting = measure.getID();
            sequenceNumOfMeasureCurrentlyExecuting = measure.getSequenceNumber();
            if (measure.execute(this) == RecoveryMeasure.STATUS_EXECUTED) {
                HiddenPreferences.setLatestRecoveryMeasureExecuted(measure.getSequenceNumber());
                executed.add(measure.getID());
            } else {
                // Either the execution failed or we're waiting for some update from an async process
                break;
            }
        }

        for (Integer id : executed) {
            storage.remove(id);
        }
    }

    private void proceedAfterAsyncExecutionSuccess() {
        SqlStorage<RecoveryMeasure> storage =
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        RecoveryMeasure justSucceeded = storage.read(this.idOfMeasureCurrentlyExecuting);
        HiddenPreferences.setLatestRecoveryMeasureExecuted(justSucceeded.getSequenceNumber());
        storage.remove(justSucceeded.getID());

        // so that we pick back up with the next measure, if there are any more
        executePendingMeasures();
    }

    @Override
    public void reportSuccess(boolean b) {
        System.out.println("App install succeeded for recovery measure " + sequenceNumOfMeasureCurrentlyExecuting);
        proceedAfterAsyncExecutionSuccess();
    }

    @Override
    public void failMissingResource(UnresolvedResourceException ure, AppInstallStatus statusmissing) {
        System.out.println("App install failed with missing resource for recovery measure " + sequenceNumOfMeasureCurrentlyExecuting);
    }

    @Override
    public void failInvalidResource(InvalidResourceException e, AppInstallStatus statusmissing) {
        System.out.println("App install failed with invalid resource for recovery measure " + sequenceNumOfMeasureCurrentlyExecuting);
    }

    @Override
    public void failBadReqs(String vReq, String vAvail, boolean majorIsProblem) {
        System.out.println("App install failed with bad reqs for recovery measure " + sequenceNumOfMeasureCurrentlyExecuting);
    }

    @Override
    public void failUnknown(AppInstallStatus statusfailunknown) {
        System.out.println("App install failed for unknown reason for recovery measure " + sequenceNumOfMeasureCurrentlyExecuting);
    }

    @Override
    public void updateResourceProgress(int done, int pending, int phase) {
    }

    @Override
    public void failWithNotification(AppInstallStatus statusfailstate) {
        System.out.println("App install failed with notification for recovery measure " +
                sequenceNumOfMeasureCurrentlyExecuting + ": " + statusfailstate.getLocaleKeyBase());
    }

    @Override
    public void failTargetMismatch() {
        System.out.println("App install failed with target mismatch for recovery measure " + sequenceNumOfMeasureCurrentlyExecuting);
    }
}
