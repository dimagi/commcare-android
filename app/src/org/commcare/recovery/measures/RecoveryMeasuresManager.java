package org.commcare.recovery.measures;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.models.database.SqlStorage;
import org.commcare.util.LogTypes;
import org.commcare.utils.StorageUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 4/27/18.
 */

public class RecoveryMeasuresManager {

    static final String RECOVERY_MEASURES_LAST_STATUS = "recovery-measures-last-status";

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
        if (CommCareApplication.instance().getCurrentApp() == null) {
            return false;
        }
        SqlStorage<RecoveryMeasure> storage = CommCareApplication.instance().getAppStorage(RecoveryMeasure.class);
        return storage.getNumRecords() > 0 &&
                !StorageUtils.getPendingRecoveryMeasuresInOrder(storage).get(0).triedTooRecently();
    }

    public static void handleExecutionActivityResult(Activity receiver, Intent intent) {
        if (intent != null) {
            int lastExecutionStatus = intent.getIntExtra(RECOVERY_MEASURES_LAST_STATUS, 0);
            if (lastExecutionStatus == RecoveryMeasure.STATUS_FAILED) {
                Toast.makeText(receiver, Localization.get("recovery.measure.execution.failed"), Toast.LENGTH_SHORT).show();
            }
        }
    }

}
