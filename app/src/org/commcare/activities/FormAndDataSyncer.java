package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.R;
import org.commcare.interfaces.SyncCapableCommCareActivity;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.interfaces.WithUIController;
import org.commcare.models.database.SqlStorage;
import org.commcare.network.DataPullRequester;
import org.commcare.network.LocalDataPullResponseFactory;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.suite.model.OfflineUserRestore;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.StorageUtils;
import org.javarosa.core.model.User;
import org.javarosa.core.reference.InvalidReferenceException;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.services.locale.Localization;

import java.io.IOException;

/**
 * Processes and submits forms and syncs data with server
 */
public class FormAndDataSyncer {

    public FormAndDataSyncer() {
    }

    @SuppressLint("NewApi")
    public void processAndSendForms(final SyncCapableCommCareActivity activity,
                                    FormRecord[] records,
                                    final boolean syncAfterwards,
                                    final boolean userTriggered) {

        ProcessAndSendTask<SyncCapableCommCareActivity> processAndSendTask =
                new ProcessAndSendTask<SyncCapableCommCareActivity>(activity, getFormPostURL(activity), syncAfterwards) {

            @Override
            protected void deliverResult(SyncCapableCommCareActivity receiver, FormUploadResult result) {
                if (CommCareApplication._().isConsumerApp()) {
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

                int successfulSends = this.getSuccessfulSends();

                if (result == FormUploadResult.FULL_SUCCESS) {
                    String label = Localization.get("sync.success.sent.singular",
                            new String[]{String.valueOf(successfulSends)});
                    if (successfulSends > 1) {
                        label = Localization.get("sync.success.sent",
                                new String[]{String.valueOf(successfulSends)});
                    }
                    receiver.reportSyncSuccess(label);

                    if (syncAfterwards) {
                        syncDataForLoggedInUser(receiver, true, userTriggered);
                    }
                } else if (result == FormUploadResult.AUTH_FAILURE) {
                    receiver.reportSyncFailure(Localization.get("sync.fail.auth.loggedin"), true);
                } else if (result != FormUploadResult.FAILURE) {
                    // Tasks with failure result codes will have already created a notification
                    receiver.reportSyncFailure(Localization.get("sync.fail.unsent"), true);
                }
            }

            @Override
            protected void deliverUpdate(SyncCapableCommCareActivity receiver, Long... update) {
            }

            @Override
            protected void deliverError(SyncCapableCommCareActivity receiver, Exception e) {
                receiver.reportSyncFailure(Localization.get("sync.fail.unsent"), true);
            }
        };

        processAndSendTask.setListeners(CommCareApplication._().getSession().startDataSubmissionListener());
        processAndSendTask.connect(activity);
        processAndSendTask.executeParallel(records);
    }

    private static String getFormPostURL(final Context context) {
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        return settings.getString(CommCareServerPreferences.PREFS_SUBMISSION_URL_KEY,
                context.getString(R.string.PostURL));
    }

    public void syncDataForLoggedInUser(final SyncCapableCommCareActivity activity,
                                        final boolean formsToSend, final boolean userTriggeredSync) {
        User u = CommCareApplication._().getSession().getLoggedInUser();

        if (User.TYPE_DEMO.equals(u.getUserType())) {
            if (userTriggeredSync) {
                // Remind the user that there's no syncing in demo mode.
                if (formsToSend) {
                    activity.reportSyncFailure(Localization.get("main.sync.demo.has.forms"), false);
                } else {
                    activity.reportSyncFailure(Localization.get("main.sync.demo.no.forms"), false);
                }
            }
            return;
        }

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        syncData(activity, formsToSend, userTriggeredSync,
                prefs.getString(CommCareServerPreferences.PREFS_DATA_SERVER_KEY, activity.getString(R.string.ota_restore_url)),
                u.getUsername(), u.getCachedPwd());
    }

    /**
     * @return Were forms sent to the server by this method invocation?
     */
    public boolean checkAndStartUnsentFormsTask(SyncCapableCommCareActivity activity,
                                                final boolean syncAfterwards,
                                                boolean userTriggered) {
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        FormRecord[] records = StorageUtils.getUnsentRecords(storage);

        if (records.length > 0) {
            processAndSendForms(activity, records, syncAfterwards, userTriggered);
            return true;
        } else {
            return false;
        }
    }

    public void performOtaRestore(LoginActivity context, String username, String password) {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        syncData(context, false, false,
                prefs.getString(CommCareServerPreferences.PREFS_DATA_SERVER_KEY, context.getString(R.string.ota_restore_url)),
                username,
                password);
    }

    public <I extends CommCareActivity & PullTaskResultReceiver> void performLocalRestore(
            I context,
            String username,
            String password) {

        try {
            ReferenceManager._().DeriveReference(
                    SingleAppInstallation.LOCAL_RESTORE_REFERENCE).getStream();
        } catch (InvalidReferenceException | IOException e) {
            throw new RuntimeException("Local restore file missing");
        }

        LocalDataPullResponseFactory.setRequestPayloads(new String[]{SingleAppInstallation.LOCAL_RESTORE_REFERENCE});
        syncData(context, false, false, "fake-server-that-is-never-used", username, password,
                LocalDataPullResponseFactory.INSTANCE, true);
    }

    public <I extends CommCareActivity & PullTaskResultReceiver> void performDemoUserRestore(
            I context,
            OfflineUserRestore offlineUserRestore) {
        String[] demoUserRestore = new String[]{offlineUserRestore.getReference()};
        LocalDataPullResponseFactory.setRequestPayloads(demoUserRestore);
        syncData(context, false, false, "fake-server-that-is-never-used",
                offlineUserRestore.getUsername(), OfflineUserRestore.DEMO_USER_PASSWORD,
                LocalDataPullResponseFactory.INSTANCE, true);
    }

    public <I extends CommCareActivity & PullTaskResultReceiver> void syncData(
            final I activity, final boolean formsToSend,
            final boolean userTriggeredSync, String server,
            String username, String password) {

        syncData(activity, formsToSend, userTriggeredSync, server, username, password,
                CommCareApplication._().getDataPullRequester(), false);
    }

    private <I extends CommCareActivity & PullTaskResultReceiver> void syncData(
            final I activity, final boolean formsToSend,
            final boolean userTriggeredSync, String server,
            String username, String password,
            DataPullRequester dataPullRequester, boolean blockRemoteKeyManagement) {

        DataPullTask<PullTaskResultReceiver> dataPullTask = new DataPullTask<PullTaskResultReceiver>(
                username, password, server, activity, dataPullRequester, blockRemoteKeyManagement) {

            @Override
            protected void deliverResult(PullTaskResultReceiver receiver,
                                         ResultAndError<PullTaskResult> resultAndErrorMessage) {
                receiver.handlePullTaskResult(resultAndErrorMessage, userTriggeredSync, formsToSend);
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
