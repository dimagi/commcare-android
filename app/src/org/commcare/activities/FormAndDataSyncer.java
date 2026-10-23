package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import org.commcare.CommCareApplication;
import org.commcare.home.SyncDelegate;
import org.commcare.interfaces.WithUIController;
import org.commcare.network.DataPullRequester;
import org.commcare.network.mocks.LocalFilePullResponseFactory;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.sync.ProcessAndSendTask;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.FormUploadResult;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;

import java.io.File;

/**
 * Processes and submits forms and syncs data with server
 */
public class FormAndDataSyncer {

    public FormAndDataSyncer() {
    }

    public void startUnsentFormsTask(
            SyncDelegate receiver,
            final boolean syncAfterwards,
            boolean userTriggered
    ) {

        // We only want to update the last upload sync time when it's a blocking sync
        if (syncAfterwards) {
            HiddenPreferences.updateLastUploadSyncAttemptTime();
        }

        processAndSendForms(receiver, syncAfterwards, userTriggered);
    }

    @SuppressLint("NewApi")
    protected void processAndSendForms(
            final SyncDelegate receiver,
            final boolean syncAfterwards,
            final boolean userTriggered
    ) {
        CommCareActivity<?> connector = receiver.getConnector();
        ProcessAndSendTask<SyncDelegate> processAndSendTask =
                new ProcessAndSendTask<SyncDelegate>(connector, syncAfterwards) {

                    @Override
                    protected void deliverResult(
                            SyncDelegate receiver,
                            FormUploadResult result
                    ) {
                        if (CommCareApplication.instance().isConsumerApp()) {
                            // if this is a consumer app we don't want to show anything in the UI about
                            // sending forms, or do a sync afterward
                            return;
                        }

                        CommCareActivity<?> activity = receiver.getConnector();
                        if (result == FormUploadResult.PROGRESS_LOGGED_OUT) {
                            activity.finish();
                            return;
                        }

                        if (activity instanceof WithUIController) {
                            ((WithUIController)activity).getUIController().refreshView();
                        }

                        receiver.handleFormUploadResult(
                                result,
                                getLabelForFormsSent(),
                                userTriggered
                        );

                        if (result == FormUploadResult.FULL_SUCCESS && syncAfterwards) {
                            syncDataForLoggedInUser(receiver, true, userTriggered);
                        }
                    }

                    @Override
                    protected void deliverUpdate(
                            SyncDelegate receiver,
                            Long... update
                    ) {
                    }

                    @Override
                    protected void deliverError(SyncDelegate receiver, Exception e) {
                        receiver.handleFormUploadResult(
                                FormUploadResult.UNSENT,
                                getLabelForFormsSent(),
                                userTriggered
                        );
                    }

                    @Override
                    protected void handleCancellation(SyncDelegate receiver) {
                        super.handleCancellation(receiver);
                        receiver.handleFormUploadResult(
                                FormUploadResult.CANCELLED,
                                getLabelForFormsSent(),
                                userTriggered
                        );
                    }
                };

        if (CommCareApplication.notificationManager().areNotificationsEnabled()) {
            processAndSendTask.addSubmissionListener(
                    CommCareApplication.instance().getSession().getListenerForSubmissionNotification());
        }

        processAndSendTask.connect((CommCareTaskConnector)connector);
        processAndSendTask.executeParallel();
    }

    public void syncDataForLoggedInUser(
            final SyncDelegate receiver,
            final boolean formsToSend,
            final boolean userTriggeredSync
    ) {
        User u = CommCareApplication.instance().getSession().getLoggedInUser();

        if (User.TYPE_DEMO.equals(u.getUserType())) {
            if (userTriggeredSync) {
                // Remind the user that there's no syncing in demo mode.
                if (formsToSend) {
                    receiver.handleSyncNotAttempted(Localization.get("main.sync.demo.has.forms"));
                } else {
                    receiver.handleSyncNotAttempted(Localization.get("main.sync.demo.no.forms"));
                }
            }
            return;
        }

        syncData(
                receiver.getConnector(),
                formsToSend,
                userTriggeredSync,
                ServerUrls.getDataServerKey(),
                u.getUsername(),
                u.getCachedPwd(),
                u.getUniqueId()
        );
    }

    public void performCustomRestoreFromFile(
            CommCareActivity<?> context,
            File incomingRestoreFile
    ) {
        User u = CommCareApplication.instance().getSession().getLoggedInUser();
        String username = u.getUsername();

        LocalFilePullResponseFactory.setRequestPayloads(new File[]{incomingRestoreFile});
        syncData(
                context,
                false,
                false,
                "fake-server-that-is-never-used",
                username,
                null,
                "unused",
                LocalFilePullResponseFactory.INSTANCE,
                true
        );
    }

    public void syncData(
            final CommCareActivity<?> activity,
            final boolean formsToSend,
            final boolean userTriggeredSync,
            String server,
            String username,
            String password,
            String userId
    ) {

        syncData(
                activity,
                formsToSend,
                userTriggeredSync,
                server,
                username,
                password,
                userId,
                CommCareApplication.instance().getDataPullRequester(),
                false
        );
    }

    private void syncData(
            final CommCareActivity<?> activity,
            final boolean formsToSend,
            final boolean userTriggeredSync,
            String server,
            String username,
            String password,
            String userId,
            DataPullRequester dataPullRequester,
            boolean blockRemoteKeyManagement
    ) {

        DataPullTask<PullTaskResultReceiver> dataPullTask = new DataPullTask<PullTaskResultReceiver>(
                username,
                password,
                userId,
                server,
                activity,
                dataPullRequester,
                blockRemoteKeyManagement,
                false,
                userTriggeredSync
        ) {

            @Override
            protected void deliverResult(
                    PullTaskResultReceiver receiver,
                    ResultAndError<PullTaskResult> resultAndErrorMessage
            ) {
                receiver.handlePullTaskResult(
                        resultAndErrorMessage, userTriggeredSync, formsToSend,
                        !blockRemoteKeyManagement
                );
            }

            @Override
            protected void deliverUpdate(PullTaskResultReceiver receiver, Integer... update) {
                receiver.handlePullTaskUpdate(update);
            }

            @Override
            protected void deliverError(
                    PullTaskResultReceiver receiver,
                    Exception e
            ) {
                receiver.handlePullTaskError();
            }
        };

        dataPullTask.connect((CommCareTaskConnector)activity);
        dataPullTask.executeParallel();
    }
}
