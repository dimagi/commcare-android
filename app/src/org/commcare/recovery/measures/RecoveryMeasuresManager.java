package org.commcare.recovery.measures;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.StorageUtils;

/**
 * Created by amstone326 on 4/27/18.
 */

public class RecoveryMeasuresManager {

    public static void requestRecoveryMeasures() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            // There's nothing we can do if we don't know what app to request recovery measures from
            return;
        }
        (new RecoveryMeasuresRequester()).makeRequest();
    }

    public static boolean recoveryMeasuresPending() {
        return CommCareApplication.instance().getAppStorage(RecoveryMeasure.class).getNumRecords() > 0;
    }

    public static void startExecutionTask(CommCareActivity activity) {
        ExecuteRecoveryMeasuresTask task = new ExecuteRecoveryMeasuresTask();
        task.connect(activity);
        task.executeParallel();
    }

    protected static void executePendingMeasures() {
        for (RecoveryMeasure measure : StorageUtils.getPendingRecoveryMeasuresInOrder()) {
            boolean success = measure.execute();
            if (success) {
                HiddenPreferences.setLatestRecoveryMeasureExecuted(measure.getSequenceNumber());
            } else {
                // TODO: What to do here? Should we assume this will never work and keep going?
                break;
            }
        }
    }

}
