package org.commcare.android.mocks;

import android.util.Log;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.commcare.dalvik.activities.FormAndDataSyncer;

/**
 * Fake object that prevent tests from communicating with server to pull or submit data
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class FormAndDataSyncerFake extends FormAndDataSyncer {
    private final String TAG = FormAndDataSyncerFake.class.getSimpleName();

    public FormAndDataSyncerFake(CommCareHomeActivity activity) {
        super(activity);
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
