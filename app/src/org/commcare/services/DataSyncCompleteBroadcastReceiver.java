package org.commcare.services;

import static org.commcare.activities.EntitySelectActivity.EXTRA_ENTITY_KEY;
import static org.commcare.activities.HomeScreenBaseActivity.RESULT_RESTART;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.SessionFrame;

public class DataSyncCompleteBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AndroidSessionWrapper asw = CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareActivity activity = (CommCareActivity) context;
        Intent returnIntent = new Intent();
        setSelectedEntity(activity, returnIntent);
        activity.setResult(RESULT_RESTART, returnIntent);
        activity.finish();
    }

    /**
     * This is to be used when rebuilding the session to trigger the select detail logic
     *
     * @param activity
     * @param returnIntent
     */
    private void setSelectedEntity(CommCareActivity activity, Intent returnIntent) {
        if (activity instanceof EntityDetailActivity &&
                activity.getIntent().hasExtra(SessionFrame.STATE_DATUM_VAL)) {
            String selectedEntity = activity.getIntent().getStringExtra(SessionFrame.STATE_DATUM_VAL);
            returnIntent.putExtra(EXTRA_ENTITY_KEY, selectedEntity);
        }
    }
}
