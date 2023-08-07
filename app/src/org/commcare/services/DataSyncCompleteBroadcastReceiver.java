package org.commcare.services;

import static android.app.Activity.RESULT_CANCELED;

import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.ENTITY_DETAIL;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.ENTITY_SELECTION;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.FORM_ENTRY;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.HOME_SCREEN;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.MENU;
import static org.commcare.sync.FirebaseMessagingDataSyncer.AppNavigationStates.OTHER;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.MenuActivity;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.sync.FirebaseMessagingDataSyncer;

public class DataSyncCompleteBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AndroidSessionWrapper aSessWrapper = CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession commcareSession = aSessWrapper.getSession();

        CommCareActivity activity = (CommCareActivity) context;
        boolean recreateActivity = false;

        try {
            switch (getAppNavigationState(activity)) {
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

    private FirebaseMessagingDataSyncer.AppNavigationStates getAppNavigationState(CommCareActivity activity) {
        if (activity instanceof FormEntryActivity)
            return FORM_ENTRY;
        else if (activity instanceof EntitySelectActivity)
            return ENTITY_SELECTION;
        else if (activity instanceof EntityDetailActivity)
            return ENTITY_DETAIL;
        else if (activity instanceof StandardHomeActivity)
            return HOME_SCREEN;
        else if (activity instanceof MenuActivity)
            return MENU;
        return OTHER;
    }
}
