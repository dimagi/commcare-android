package org.commcare.recovery.measures;

import org.commcare.activities.CommCareActivity;
import org.commcare.tasks.templates.CommCareTask;

/**
 * Created by amstone326 on 5/18/18.
 */

public class ExecuteRecoveryMeasuresTask extends CommCareTask<Void, Void, Boolean, CommCareActivity> {

    @Override
    protected Boolean doTaskBackground(Void... voids) {
        RecoveryMeasuresManager.executePendingMeasures();
        return true;
    }

    @Override
    protected void deliverResult(CommCareActivity commCareActivity, Boolean aBoolean) {

    }

    @Override
    protected void deliverUpdate(CommCareActivity commCareActivity, Void... update) {

    }

    @Override
    protected void deliverError(CommCareActivity commCareActivity, Exception e) {

    }
}
