package org.commcare.recovery.measures;

import org.commcare.activities.BlockingProcessActivity;

/**
 * Created by amstone326 on 5/22/18.
 */
public class ExecuteRecoveryMeasuresActivity extends BlockingProcessActivity {


    @Override
    protected String getDisplayTextKey() {
        return "executing.recovery.measures";
    }

    @Override
    protected Runnable buildProcessToRun(ThreadHandler handler) {
        return () -> {
            RecoveryMeasuresManager.executePendingMeasures();
            handler.sendEmptyMessage(0);
        };
    }
}
