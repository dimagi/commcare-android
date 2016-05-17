package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.installers.SingleAppInstallation;
import org.commcare.network.DataPullRequester;
import org.commcare.network.DataPullResponseFactory;
import org.commcare.network.LocalDataPullResponseFactory;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.PullTaskReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.SessionUnavailableException;
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
    public void processAndSendForms(final CommCareHomeActivity activity,
                                    FormRecord[] records,
                                    final boolean syncAfterwards,
                                    final boolean userTriggered) {

        ProcessAndSendTask<CommCareHomeActivity> mProcess = new ProcessAndSendTask<CommCareHomeActivity>(
                activity,
                getFormPostURL(activity),
                syncAfterwards) {

            @Override
            protected void deliverResult(CommCareHomeActivity receiver, Integer result) {
                if (CommCareApplication._().isConsumerApp()) {
                    // if this is a consumer app we don't want to show anything in the UI about
                    // sending forms, or do a sync afterward
                    return;
                }

                if (result == ProcessAndSendTask.PROGRESS_LOGGED_OUT) {
                    receiver.finish();
                    return;
                }
                receiver.getUIController().refreshView();

                int successfulSends = this.getSuccessfulSends();

                if (result == FormUploadUtil.FULL_SUCCESS) {
                    String label = Localization.get("sync.success.sent.singular",
                            new String[]{String.valueOf(successfulSends)});
                    if (successfulSends > 1) {
                        label = Localization.get("sync.success.sent",
                                new String[]{String.valueOf(successfulSends)});
                    }
                    receiver.reportSuccess(label);

                    if (syncAfterwards) {
                        syncDataForLoggedInUser(receiver, true, userTriggered);
                    }
                } else if (result != FormUploadUtil.FAILURE) {
                    // Tasks with failure result codes will have already created a notification
                    receiver.reportFailure(Localization.get("sync.fail.unsent"), true);
                }
            }

            @Override
            protected void deliverUpdate(CommCareHomeActivity receiver, Long... update) {
            }

            @Override
            protected void deliverError(CommCareHomeActivity receiver, Exception e) {
                receiver.reportFailure(Localization.get("sync.fail.unsent"), true);
            }
        };

        try {
            mProcess.setListeners(CommCareApplication._().getSession().startDataSubmissionListener());
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }
        mProcess.connect(activity);

        //Execute on a true multithreaded chain. We should probably replace all of our calls with this
        //but this is the big one for now.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mProcess.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, records);
        } else {
            mProcess.execute(records);
        }

    }

    private static String getFormPostURL(final Context context) {
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        return settings.getString(CommCarePreferences.PREFS_SUBMISSION_URL_KEY,
                context.getString(R.string.PostURL));
    }

    private <I extends CommCareActivity & PullTaskReceiver> void syncData(
            final I activity, final boolean formsToSend,
            final boolean userTriggeredSync, String server,
            String username, String password) {

        syncData(activity, formsToSend, userTriggeredSync, server, username, password, CommCareApplication._().getDataPullRequester());
    }

    private <I extends CommCareActivity & PullTaskReceiver> void syncData(
            final I activity, final boolean formsToSend,
            final boolean userTriggeredSync, String server,
            String username, String password,
            DataPullRequester dataPullRequester) {

        DataPullTask<PullTaskReceiver> mDataPullTask = new DataPullTask<PullTaskReceiver>(
                username,
                password,
                server,
                activity,
                dataPullRequester) {

            @Override
            protected void deliverResult(PullTaskReceiver receiver, ResultAndError<PullTaskResult> resultAndErrorMessage) {
                receiver.handlePullTaskResult(resultAndErrorMessage, userTriggeredSync, formsToSend);
            }

            @Override
            protected void deliverUpdate(PullTaskReceiver receiver, Integer... update) {
                receiver.handlePullTaskUpdate(update);
            }

            @Override
            protected void deliverError(PullTaskReceiver receiver,
                                        Exception e) {
                receiver.handlePullTaskError(e);
            }
        };

        mDataPullTask.connect(activity);
        mDataPullTask.executeParallel();
    }

    public void syncDataForLoggedInUser(
            final CommCareHomeActivity activity,
            final boolean formsToSend,
            final boolean userTriggeredSync) {

        User u;
        try {
            u = CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }

        if (User.TYPE_DEMO.equals(u.getUserType())) {
            if (userTriggeredSync) {
                // Remind the user that there's no syncing in demo mode.
                if (formsToSend) {
                    activity.displayMessage(Localization.get("main.sync.demo.has.forms"), true, true);
                } else {
                    activity.displayMessage(Localization.get("main.sync.demo.no.forms"), true, true);
                }
            }
            return;
        }

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        syncData(activity, formsToSend, userTriggeredSync,
                prefs.getString(CommCarePreferences.PREFS_DATA_SERVER_KEY, activity.getString(R.string.ota_restore_url)),
                u.getUsername(), u.getCachedPwd());
    }

    public void performOtaRestore(LoginActivity context, String username, String password) {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        syncData(context, false, false,
                prefs.getString(CommCarePreferences.PREFS_DATA_SERVER_KEY, context.getString(R.string.ota_restore_url)),
                username,
                password);
    }

    public <I extends CommCareActivity & PullTaskReceiver> void performLocalRestore(
            I context,
            String username,
            String password) {

        try {
            ReferenceManager._().DeriveReference(
                    SingleAppInstallation.LOCAL_RESTORE_REFERENCE).getStream();
        } catch (InvalidReferenceException | IOException e) {
            throw new RuntimeException("Local restore file missing");
        }

        LocalDataPullResponseFactory.setRequestPayloads(new String[] {SingleAppInstallation.LOCAL_RESTORE_REFERENCE});
        syncData(context, false, false, "fake-server-that-is-never-used", username, password, LocalDataPullResponseFactory.INSTANCE);
    }
}
