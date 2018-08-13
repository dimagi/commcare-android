package org.commcare.recovery.measures;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.StringUtils;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by amstone326 on 4/27/18.
 */

public class RecoveryMeasuresHelper {

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
        List<RecoveryMeasure> pendingMeasures = getPendingRecoveryMeasuresInOrder(storage);
        return pendingMeasures.size() > 0 &&
                !getPendingRecoveryMeasuresInOrder(storage).get(0).triedTooRecently();
    }

    public static List<RecoveryMeasure> getPendingRecoveryMeasuresInOrder(SqlStorage<RecoveryMeasure> storage) {
        List<RecoveryMeasure> toExecute = new ArrayList<>();
        List<RecoveryMeasure> toDelete = new ArrayList<>();

        long latestMeasureExecuted = HiddenPreferences.getLatestRecoveryMeasureExecuted();
        for (RecoveryMeasure measure : storage) {
            if (measure.getSequenceNumber() <= latestMeasureExecuted) {
                toDelete.add(measure);
            } else {
                toExecute.add(measure);
            }
        }

        for (RecoveryMeasure measure : toDelete) {
            storage.remove(measure.getID());
        }

        Collections.sort(toExecute, (measure1, measure2) -> {
            long diff = measure1.getSequenceNumber() - measure2.getSequenceNumber();
            if (diff < 0) {
                return -1;
            } else if (diff == 0) {
                return 0;
            } else {
                return 1;
            }
        });
        return toExecute;
    }

    public static void handleExecutionActivityResult(Activity receiver, Intent intent) {
        if (intent != null) {
            int lastExecutionStatus = intent.getIntExtra(RECOVERY_MEASURES_LAST_STATUS, 0);
            if (lastExecutionStatus == RecoveryMeasure.STATUS_FAILED) {
                Toast.makeText(receiver, StringUtils.getStringRobust(receiver, R.string.recovery_measure_faiure), Toast.LENGTH_LONG).show();
            }
        }
    }

}
