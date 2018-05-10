package org.commcare.recovery.measures;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by amstone326 on 5/8/18.
 */

public class RecoveryMeasuresFoundReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        RecoveryMeasuresManager.executePendingMeasures();
    }
}
