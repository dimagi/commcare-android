package org.commcare.android.mocks;

import android.util.Log;

import org.commcare.activities.FormAndDataSyncer;
import org.commcare.activities.SyncCapableCommCareActivity;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.utils.FormUploadResult;

/**
 * Created by amstone326 on 6/14/17.
 */

public class FormAndDataSyncerWithCustomResult extends FormAndDataSyncer {

    private final String TAG = FormAndDataSyncerWithCustomResult.class.getSimpleName();

    FormUploadResult[] customResultsToUse;

    public FormAndDataSyncerWithCustomResult(FormUploadResult[] customResultsToUse) {
        this.customResultsToUse = customResultsToUse;
    }

    @Override
    public void processAndSendForms(SyncCapableCommCareActivity activity, FormRecord[] records,
                                    final boolean syncAfterwards, final boolean userTriggered) {
        ProcessAndSendTask<SyncCapableCommCareActivity> processAndSendTask =
                new ProcessAndSendTaskMock(activity, customResultsToUse) {

                    @Override
                    protected void deliverResult(SyncCapableCommCareActivity syncCapableCommCareActivity, FormUploadResult formUploadResult) {

                    }

                    @Override
                    protected void deliverUpdate(SyncCapableCommCareActivity syncCapableCommCareActivity, Long... update) {

                    }

                    @Override
                    protected void deliverError(SyncCapableCommCareActivity syncCapableCommCareActivity, Exception e) {

                    }
                };

        processAndSendTask.connect(activity);
        processAndSendTask.executeParallel(records);
    }

    @Override
    public void syncDataForLoggedInUser(final SyncCapableCommCareActivity activity,
                                        boolean formsToSend, boolean userTriggeredSync) {
        Log.d(TAG, "faking data sync");
    }
}
