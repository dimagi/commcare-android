package org.commcare.services;

import static android.app.Activity.RESULT_CANCELED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;

public class DataSyncCompleteBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AndroidSessionWrapper aSessWrapper = CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession commcareSession = aSessWrapper.getSession();

        CommCareActivity activity = (CommCareActivity) context;
        boolean recreateActivity = false;

        try {
            commcareSession.syncState();
            if (recreateActivity) {
                activity.recreate();
            }
        }
        catch(RuntimeException e){
            activity.setResult(RESULT_CANCELED);
            activity.finish();
        }
    }
}
