package org.commcare.recovery.measures;

import android.app.Activity;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.commcare.utils.StorageUtils;
import org.javarosa.core.services.Logger;

/**
 * Created by amstone326 on 4/27/18.
 */

public class RecoveryMeasuresManager {

    private static final int EXECUTION_ATTEMPTS_LIMIT = 3;

    public static void requestRecoveryMeasures() {
        if (CommCareApplication.instance().getCurrentApp() == null) {
            // There's nothing we can do if we don't know what app to request recovery measures from
            return;
        }

        Thread requestThread = new Thread(() -> {
            try {
                new RecoveryMeasuresRequester().makeRequest();
            } catch (Exception e) {
                Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, String.format(
                        "Encountered unexpected exception during recovery measures request: %s",
                        e.getMessage()
                ));
            }
        });

        requestThread.run();
    }

    public static boolean recoveryMeasuresPending() {
        return CommCareApplication.instance().getAppStorage(RecoveryMeasure.class).getNumRecords() > 0;
    }

    public static void startExecutionActivity(Activity origin) {
        origin.startActivity(new Intent(origin, ExecuteRecoveryMeasuresActivity.class));
    }

    static void executePendingMeasures() {
        SqlStorage<RecoveryMeasure> storage =
                CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        for (RecoveryMeasure measure : StorageUtils.getPendingRecoveryMeasuresInOrder(storage)) {
            if (measure.execute()) {
                HiddenPreferences.setLatestRecoveryMeasureExecuted(measure.getSequenceNumber());
                storage.remove(measure.getID());
            } else {
                measure.incrementAttempts();
                if (measure.getAttempts() >= EXECUTION_ATTEMPTS_LIMIT) {
                    // We've reached the limit for # of times we're going to try this,
                    // so delete it and move on
                    storage.remove(measure.getID());
                } else {
                    // Update the # of attempts made and stop here for now
                    storage.write(measure);
                    break;
                }
            }
        }
    }

}
