package org.commcare.services;

import static android.app.Activity.RESULT_CANCELED;
import static org.commcare.activities.HomeScreenBaseActivity.RESULT_RESTART;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.activities.HomeScreenBaseActivity;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.SessionFrame;

public class DataSyncCompleteBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AndroidSessionWrapper asw = CommCareApplication.instance().getCurrentSessionWrapper();

        CommCareActivity activity = (CommCareActivity) context;
        if (!(activity instanceof HomeScreenBaseActivity)){
            // We only rebuild the session if the user is not in the Home Screen
            try {
                // Restart activity and let the Session rebuild itself

                if (activity instanceof EntityDetailActivity)
                    saveSelectedEntity(activity);
                asw.cleanVolatiles();
                activity.setResult(RESULT_RESTART);
            } catch (RuntimeException e) {
                activity.setResult(RESULT_CANCELED);
            }
            finally {
                activity.finish();
            }
        }
    }

    /**
     * This is to be used when rebuilding the session to trigger the select detail logic
     * @param activity
     */
    private void saveSelectedEntity(CommCareActivity activity) {
        if (activity.getIntent().hasExtra(SessionFrame.STATE_DATUM_VAL)) {
            HomeScreenBaseActivity.selectedEntityPostSync = activity.getIntent().getStringExtra(SessionFrame.STATE_DATUM_VAL);
        }
    }
}
