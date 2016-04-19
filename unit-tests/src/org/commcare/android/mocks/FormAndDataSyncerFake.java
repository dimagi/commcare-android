package org.commcare.android.mocks;

import android.content.Context;
import android.util.Log;

import org.commcare.activities.CommCareHomeActivity;
import org.commcare.activities.FormAndDataSyncer;
import org.commcare.android.database.user.models.FormRecord;

/**
 * Fake object that prevent tests from communicating with server to pull or submit data
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FormAndDataSyncerFake extends FormAndDataSyncer {
    private final String TAG = FormAndDataSyncerFake.class.getSimpleName();

    public FormAndDataSyncerFake(CommCareHomeActivity activity) {
        super((Context)activity, activity);
    }

    @Override
    public void processAndSendForms(FormRecord[] records,
                                    final boolean syncAfterwards,
                                    final boolean userTriggered) {
        Log.d(TAG, "faking form processing and sending");
    }

    @Override
    public void syncData(boolean formsToSend,
                         boolean userTriggeredSync) {
        Log.d(TAG, "faking data sync");
    }
}
