package org.commcare.android.mocks;

import android.util.Log;

import org.commcare.activities.FormAndDataSyncer;
import org.commcare.home.SyncDelegate;

/**
 * Fake object that prevent tests from communicating with server to pull or submit data
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FormAndDataSyncerFake extends FormAndDataSyncer {
    private final String TAG = FormAndDataSyncerFake.class.getSimpleName();

    public int formSendCount = 0;
    public boolean lastSendSyncsAfterwards;
    public boolean lastSendUserTriggered;
    public int dataSyncCount = 0;

    public FormAndDataSyncerFake() {
    }

    @Override
    protected void processAndSendForms(SyncDelegate receiver, boolean syncAfterwards, boolean userTriggered) {
        Log.d(TAG, "faking form processing and sending");
        formSendCount++;
        lastSendSyncsAfterwards = syncAfterwards;
        lastSendUserTriggered = userTriggered;
    }

    @Override
    public void syncDataForLoggedInUser(final SyncDelegate receiver,
                                        boolean formsToSend, boolean userTriggeredSync) {
        Log.d(TAG, "faking data sync");
        dataSyncCount++;
    }
    
}
