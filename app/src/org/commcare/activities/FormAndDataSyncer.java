package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import org.commcare.CommCareApplication;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.interfaces.WithUIController;
import org.commcare.network.DataPullRequester;
import org.commcare.network.LocalReferencePullResponseFactory;
import org.commcare.network.mocks.LocalFilePullResponseFactory;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.FormSubmissionProgressBarListener;
import org.commcare.sync.ProcessAndSendTask;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.FormUploadResult;
import org.javarosa.core.model.User;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.IOException;

/**
 * Processes and submits forms and syncs data with server
 */
public class FormAndDataSyncer {

    protected FormAndDataSyncer() {
    }


    void startUnsentFormsTask(SyncCapableCommCareActivity activity,
                              final boolean syncAfterwards,
                              boolean userTriggered) {

        // We only want to update the last upload sync time when it's a blocking sync
        if (syncAfterwards) {
            HiddenPreferences.updateLastUploadSyncAttemptTime();
        }

        processAndSendForms(activity, syncAfterwards, userTriggered);
    }

    @SuppressLint("NewApi")
    protected void processAndSendForms(final SyncCapableCommCareActivity activity,
                                     final boolean syncAfterwards,
                                     final boolean userTriggered) {

        ProcessAndSendTask<SyncCapableCommCareActivity> processAndSendTask =
                new ProcessAndSendTask<SyncCapableCommCareActivity>(activity, syncAfterwards) {

                    @Override
                    protected void deliverResult(SyncCapableCommCareActivity receiver, FormUploadResult result) {
                        if (CommCareApplication.instance().isConsumerApp()) {
                            // if this is a consumer app we don't want to show anything in the UI about
                            // sending forms, or do a sync afterward
                            return;
                        }

                        if (result == FormUploadResult.PROGRESS_LOGGED_OUT) {
                            receiver.finish();
                            return;
                        }

                        if (receiver instanceof WithUIController) {
                            ((WithUIController)receiver).getUIController().refreshView();
                        }

                        receiver.handleFormUploadResult(result, getLabelForFormsSent(), userTriggered);

                        if(result == FormUploadResult.FULL_SUCCESS && syncAfterwards) {
                            syncDataForLoggedInUser(receiver, true, userTriggered);
                        }
                    }

                    @Override
                    protected void deliverUpdate(SyncCapableCommCareActivity receiver, Long... update) {
                    }

                    @Override
                    protected void deliverError(SyncCapableCommCareActivity receiver, Exception e) {
                        receiver.handleFormUploadResult(FormUploadResult.UNSENT, getLabelForFormsSent(), userTriggered);
                    }

                    @Override
                    protected void handleCancellation(SyncCapableCommCareActivity receiver) {
                        super.handleCancellation(receiver);
                        receiver.handleFormUploadResult(FormUploadResult.CANCELLED, getLabelForFormsSent(), userTriggered);
                    }
                };

        processAndSendTask.addSubmissionListener(
                CommCareApplication.instance().getSession().getListenerForSubmissionNotification());
        if (activity.usesSubmissionProgressBar()) {
            processAndSendTask.addProgressBarSubmissionListener(
                    new FormSubmissionProgressBarListener(activity));
        }

        processAndSendTask.connect(activity);
        processAndSendTask.executeParallel();
    }


    public void syncDataForLoggedInUser(final SyncCapableCommCareActivity activity,
                                        final boolean formsToSend, final boolean userTriggeredSync) {
        User u = CommCareApplication.instance().getSession().getLoggedInUser();

        if (User.TYPE_DEMO.equals(u.getUserType())) {
            if (userTriggeredSync) {
                // Remind the user that there's no syncing in demo mode.
                if (formsToSend) {
                    activity.handleSyncNotAttempted(Localization.get("main.sync.demo.has.forms"));
                } else {
                    activity.handleSyncNotAttempted(Localization.get("main.sync.demo.no.forms"));
                }
            }
            return;
        }

        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
        syncData(activity, formsToSend, userTriggeredSync, ServerUrls.getDataServerKey(),
                u.getUsername(), u.getCachedPwd(), u.getUniqueId());
    }

    public void performOtaRestore(LoginActivity context, String username, String password) {
        syncData(context, false, false, ServerUrls.getDataServerKey(),
                username, password, null);
    }

    public <I extends CommCareActivity & PullTaskResultReceiver> void performCustomRestoreFromFile(
            I context,
            File incomingRestoreFile) {
        User u = CommCareApplication.instance().getSession().getLoggedInUser();
        String username = u.getUsername();

        LocalFilePullResponseFactory.setRequestPayloads(new File[]{incomingRestoreFile});
        syncData(context, false, false, "fake-server-that-is-never-used", username, null, "unused",
                LocalFilePullResponseFactory.INSTANCE, true);
    }


    public <I extends CommCareActivity & PullTaskResultReceiver> void performLocalRestore(
            I context,
            String username,
            String password) {

        try {
            ReferenceManager.instance().DeriveReference(
                    SingleAppInstallation.LOCAL_RESTORE_REFERENCE).getStream();
        } catch (InvalidReferenceException | IOException e) {
            throw new RuntimeException("Local restore file missing");
        }

        LocalReferencePullResponseFactory.setRequestPayloads(new String[]{SingleAppInstallation.LOCAL_RESTORE_REFERENCE});
        syncData(context, false, false, "fake-server-that-is-never-used", username, password, "unused",
                LocalReferencePullResponseFactory.INSTANCE, true);
    }


    public <I extends CommCareActivity & PullTaskResultReceiver> void performDemoUserRestore(
            I context,
            OfflineUserRestore offlineUserRestore) {
        String[] demoUserRestore = new String[]{offlineUserRestore.getReference()};
        LocalReferencePullResponseFactory.setRequestPayloads(demoUserRestore);
        syncData(context, false, false, "fake-server-that-is-never-used",
                offlineUserRestore.getUsername(), OfflineUserRestore.DEMO_USER_PASSWORD, "demo_id",
                LocalReferencePullResponseFactory.INSTANCE, true);
    }

    public <I extends CommCareActivity & PullTaskResultReceiver> void syncData(
            final I activity, final boolean formsToSend,
            final boolean userTriggeredSync, String server,
            String username, String password, String userId) {

        syncData(activity, formsToSend, userTriggeredSync, server, username, password, userId,
                CommCareApplication.instance().getDataPullRequester(), false);
    }

    private <I extends CommCareActivity & PullTaskResultReceiver> void syncData(
            final I activity, final boolean formsToSend,
            final boolean userTriggeredSync, String server,
            String username, String password, String userId,
            DataPullRequester dataPullRequester, boolean blockRemoteKeyManagement) {

        DataPullTask<PullTaskResultReceiver> dataPullTask = new DataPullTask<PullTaskResultReceiver>(
                username, password, userId, server, activity, dataPullRequester,
                blockRemoteKeyManagement, false, false) {

            @Override
            protected void deliverResult(PullTaskResultReceiver receiver,
                                         ResultAndError<PullTaskResult> resultAndErrorMessage) {
                receiver.handlePullTaskResult(resultAndErrorMessage, userTriggeredSync, formsToSend,
                        !blockRemoteKeyManagement);
            }

            @Override
            protected void deliverUpdate(PullTaskResultReceiver receiver, Integer... update) {
                receiver.handlePullTaskUpdate(update);
            }

            @Override
            protected void deliverError(PullTaskResultReceiver receiver,
                                        Exception e) {
                receiver.handlePullTaskError();
            }
        };

        dataPullTask.connect(activity);
        dataPullTask.executeParallel();
    }

}
