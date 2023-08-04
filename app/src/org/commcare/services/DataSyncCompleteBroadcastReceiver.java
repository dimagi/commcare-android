package org.commcare.services;

import static android.app.Activity.RESULT_CANCELED;

import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.ENTITY_DETAIL;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.ENTITY_SELECTION;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.MENU;

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
            switch (getAppNavigationState()) {
                case ENTITY_SELECTION:
                    aSessWrapper.cleanVolatiles();
                    ((EntitySelectActivity) activity).loadEntities();
                    break;
                case ENTITY_DETAIL:
                    aSessWrapper.cleanVolatiles();
                    recreateActivity = true;
                    break;
                case MENU:
                    recreateActivity = true;
                    break;
            }
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
