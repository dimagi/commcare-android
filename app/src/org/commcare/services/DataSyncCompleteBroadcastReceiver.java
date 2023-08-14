package org.commcare.services;

import static android.app.Activity.RESULT_CANCELED;

import static org.commcare.activities.DispatchActivity.SESSION_REBUILD_REQUEST;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.EntityDetailActivity;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.HomeScreenBaseActivity;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionDescriptorUtil;
import org.commcare.session.SessionFrame;

public class DataSyncCompleteBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        AndroidSessionWrapper asw = CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession commcareSession = asw.getSession();

        CommCareActivity activity = (CommCareActivity) context;
        if (!(activity instanceof HomeScreenBaseActivity)){
            // We only rebuild the session if the user is not in the Home Screen
            try {
                // Rebuild the Session
                Intent i = new Intent(context, DispatchActivity.class);
                i.putExtra(SESSION_REBUILD_REQUEST, SessionDescriptorUtil.createSessionDescriptor(commcareSession));
                if (context instanceof EntityDetailActivity)
                    addSelectedEntityDatumToSession(activity, commcareSession);
                context.startActivity(i);
            } catch (RuntimeException e) {
                activity.setResult(RESULT_CANCELED);
            }
            finally {
                activity.finish();
            }
        }
    }

    /**
     * This adds the selected entity to the CommCare Session to be popped from the session frame
     * and trigger the confirm entity detail logic
     */
    private void addSelectedEntityDatumToSession(CommCareActivity activity, CommCareSession session) {
        if (activity.getIntent().hasExtra(SessionFrame.STATE_DATUM_VAL)) {
            String caseId = activity.getIntent().getStringExtra(SessionFrame.STATE_DATUM_VAL);
            session.setEntityDatum(EntitySelectActivity.EXTRA_ENTITY_KEY, caseId);
        }
    }
}
