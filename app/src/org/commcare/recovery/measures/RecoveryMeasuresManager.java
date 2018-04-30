package org.commcare.recovery.measures;

import org.commcare.heartbeat.HeartbeatRequester;

/**
 * Created by amstone326 on 4/27/18.
 */

public class RecoveryMeasuresManager {

    public static void requestRecoveryMeasures() {
        HeartbeatRequester request = new HeartbeatRequester();
    }

}
