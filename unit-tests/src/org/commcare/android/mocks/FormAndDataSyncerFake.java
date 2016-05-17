package org.commcare.android.mocks;

import android.util.Log;

import org.commcare.activities.CommCareActivity;
import org.commcare.activities.CommCareHomeActivity;
import org.commcare.activities.FormAndDataSyncer;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.network.DataPullRequester;
import org.commcare.tasks.PullTaskReceiver;

/**
 * Fake object that prevent tests from communicating with server to pull or submit data
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FormAndDataSyncerFake extends FormAndDataSyncer {
    private final String TAG = FormAndDataSyncerFake.class.getSimpleName();

    public FormAndDataSyncerFake() {
    }

    @Override
    public void processAndSendForms(CommCareHomeActivity activity,
                                    FormRecord[] records,
                                    final boolean syncAfterwards,
                                    final boolean userTriggered) {
        Log.d(TAG, "faking form processing and sending");
    }

    @Override
    public void syncDataForLoggedInUser(CommCareHomeActivity activity,
                                        boolean formsToSend,
                                        boolean userTriggeredSync) {
        Log.d(TAG, "faking data sync");
    }
    
}
