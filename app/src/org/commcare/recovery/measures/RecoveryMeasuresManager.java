package org.commcare.recovery.measures;

import android.content.Intent;

import org.commcare.CommCareApplication;
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

    public static void sendBroadcast() {
        Intent i = new Intent("org.commcare.dalvik.api.action.RecoveryMeasuresFound");
        CommCareApplication.instance().sendBroadcast(i);
    }

    // Execute any recovery measures that we've received and stored
    protected static void executePendingMeasures() {
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
