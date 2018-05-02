package org.commcare.recovery.measures;

import org.commcare.heartbeat.HeartbeatRequester;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.StorageUtils;

/**
 * Created by amstone326 on 4/27/18.
 */

public class RecoveryMeasuresManager {

    public static void requestRecoveryMeasures() {
        (new HeartbeatRequester(true)).requestHeartbeat();
    }

    // Execute any recovery measures that we've received and stored
    public static void executePendingMeasures() {
        RecoveryMeasure[] measuresToExecute = StorageUtils.getPendingRecoveryMeasuresInOrder();
        for (RecoveryMeasure measure : measuresToExecute) {
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
