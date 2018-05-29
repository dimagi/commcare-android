package org.commcare.recovery.measures;

import android.app.Activity;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

/**
 * Created by amstone326 on 4/27/18.
 */

public class RecoveryMeasuresManager {

    public static void requestRecoveryMeasures() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            // There's nothing we can do if we don't know what app to request recovery measures from
            return;
        }

        new Thread(() -> {
            try {
                System.out.println("Requesting recovery measures for app " +
                        CommCareApplication.instance().getCurrentApp().getAppRecord().getDisplayName());
                new RecoveryMeasuresRequester().makeRequest();
            } catch (Exception e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, String.format(
                        "Encountered unexpected exception during recovery measures request: %s",
                        e.getMessage()
                ));
            }
        }).start();
    }

    public static boolean recoveryMeasuresPending() {
        return CommCareApplication.instance().getAppStorage(RecoveryMeasure.class).getNumRecords() > 0;
    }

    public static void startExecutionActivity(Activity origin) {
        System.out.println("Executing recovery measures for app " + CommCareApplication.instance().getCurrentApp().getAppRecord().getDisplayName());
        origin.startActivity(new Intent(origin, ExecuteRecoveryMeasuresActivity.class));
    }

}
