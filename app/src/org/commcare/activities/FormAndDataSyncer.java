package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.interfaces.ConnectorWithMessaging;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.models.database.user.models.FormRecord;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.templates.CommCareTaskConnector;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;

/**
 * Processes and submits forms and syncs data with server
 */
public class FormAndDataSyncer {
    private final ConnectorWithMessaging activity;
    private final Context context;

    public FormAndDataSyncer(Context context, ConnectorWithMessaging activity) {
        this.context = context;
        this.activity = activity;
    }

    @SuppressLint("NewApi")
    public void processAndSendForms(FormRecord[] records,
                                    final boolean syncAfterwards,
                                    final boolean userTriggered) {

        ProcessAndSendTask<CommCareHomeActivity> mProcess = new ProcessAndSendTask<CommCareHomeActivity>(
                context,
                getFormPostURL(context),
                syncAfterwards) {

            @Override
            protected void deliverResult(CommCareHomeActivity receiver, Integer result) {
                if (result == ProcessAndSendTask.PROGRESS_LOGGED_OUT) {
                    receiver.finish();
                    return;
                }

                int successfulSends = this.getSuccesfulSends();

                if (result == FormUploadUtil.FULL_SUCCESS) {
                    String label = Localization.get("sync.success.sent.singular",
                            new String[]{String.valueOf(successfulSends)});
                    if (successfulSends > 1) {
                        label = Localization.get("sync.success.sent",
                                new String[]{String.valueOf(successfulSends)});
                    }
                    receiver.displayMessage(label);

                    if (syncAfterwards) {
                        syncData(true, userTriggered);
                    }
                } else if (result != FormUploadUtil.FAILURE) {
                    // Tasks with failure result codes will have already created a notification
                    receiver.displayBadMessage(Localization.get("sync.fail.unsent"));
                }
            }

            @Override
            protected void deliverUpdate(CommCareHomeActivity receiver, Long... update) {
            }

            @Override
            protected void deliverError(CommCareHomeActivity receiver, Exception e) {
                receiver.displayBadMessage(Localization.get("sync.fail.unsent"));
            }
        };

        try {
            mProcess.setListeners(CommCareApplication._().getSession().startDataSubmissionListener());
        } catch (SessionUnavailableException sue) {
            // abort since it looks like the session expired
            return;
        }
        mProcess.connect((CommCareTaskConnector<CommCareHomeActivity>)activity);

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

    public void syncData(final boolean formsToSend, final boolean userTriggeredSync) {
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
                    activity.displayBadMessageWithoutToast(Localization.get("main.sync.demo.has.forms"));
                } else {
                    activity.displayBadMessageWithoutToast(Localization.get("main.sync.demo.no.forms"));
                }
            }
            return;
        }

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        DataPullTask<ConnectorWithMessaging> mDataPullTask = new DataPullTask<ConnectorWithMessaging>(
                u.getUsername(),
                u.getCachedPwd(),
                prefs.getString(CommCarePreferences.PREFS_DATA_SERVER_KEY,
                        context.getString(R.string.ota_restore_url)),
                context) {

            @Override
            protected void deliverResult(ConnectorWithMessaging receiver, ResultAndError<PullTaskResult> resultAndErrorMessage) {
                PullTaskResult result = resultAndErrorMessage.data;
                String reportSyncLabel = result.getCorrespondingGoogleAnalyticsLabel();
                int reportSyncValue = result.getCorrespondingGoogleAnalyticsValue();

                //TODO: SHARES _A LOT_ with login activity. Unify into service
                switch (result) {
                    case AUTH_FAILED:
                        receiver.displayBadMessage(Localization.get("sync.fail.auth.loggedin"));
                        break;
                    case BAD_DATA:
                    case BAD_DATA_REQUIRES_INTERVENTION:
                        receiver.displayBadMessage(Localization.get("sync.fail.bad.data"));
                        break;
                    case DOWNLOAD_SUCCESS:
                        if (formsToSend) {
                            reportSyncValue = GoogleAnalyticsFields.VALUE_WITH_SEND_FORMS;
                        } else {
                            reportSyncValue = GoogleAnalyticsFields.VALUE_JUST_PULL_DATA;
                        }
                        receiver.displayMessage(Localization.get("sync.success.synced"));
                        break;
                    case SERVER_ERROR:
                        receiver.displayMessage(Localization.get("sync.fail.server.error"));
                        break;
                    case UNREACHABLE_HOST:
                        receiver.displayBadMessage(Localization.get("sync.fail.bad.network"));
                        break;
                    case CONNECTION_TIMEOUT:
                        receiver.displayBadMessage(Localization.get("sync.fail.timeout"));
                        break;
                    case UNKNOWN_FAILURE:
                        receiver.displayBadMessage(Localization.get("sync.fail.unknown"));
                        break;
                }

                if (userTriggeredSync) {
                    GoogleAnalyticsUtils.reportSyncAttempt(
                            GoogleAnalyticsFields.ACTION_USER_SYNC_ATTEMPT,
                            reportSyncLabel, reportSyncValue);
                } else {
                    GoogleAnalyticsUtils.reportSyncAttempt(
                            GoogleAnalyticsFields.ACTION_AUTO_SYNC_ATTEMPT,
                            reportSyncLabel, reportSyncValue);
                }
                //TODO: What if the user info was updated?
            }

            @Override
            protected void deliverUpdate(ConnectorWithMessaging receiver, Integer... update) {
                if (update[0] == DataPullTask.PROGRESS_STARTED) {
                    receiver.updateProgress(Localization.get("sync.progress.purge"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_CLEANED) {
                    receiver.updateProgress(Localization.get("sync.progress.authing"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_AUTHED) {
                    receiver.updateProgress(Localization.get("sync.progress.downloading"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_DOWNLOADING) {
                    receiver.updateProgress(Localization.get("sync.process.downloading.progress", new String[]{String.valueOf(update[1])}), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_PROCESSING) {
                    receiver.updateProgress(Localization.get("sync.process.processing", new String[]{String.valueOf(update[1]), String.valueOf(update[2])}), DataPullTask.DATA_PULL_TASK_ID);
                    receiver.updateProgressBar(update[1], update[2], DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_RECOVERY_NEEDED) {
                    receiver.updateProgress(Localization.get("sync.recover.needed"), DataPullTask.DATA_PULL_TASK_ID);
                } else if (update[0] == DataPullTask.PROGRESS_RECOVERY_STARTED) {
                    receiver.updateProgress(Localization.get("sync.recover.started"), DataPullTask.DATA_PULL_TASK_ID);
                }
            }

            @Override
            protected void deliverError(ConnectorWithMessaging receiver,
                                        Exception e) {
                receiver.displayBadMessage(Localization.get("sync.fail.unknown"));
            }
        };

        mDataPullTask.connect((CommCareTaskConnector<ConnectorWithMessaging>)activity);
        mDataPullTask.execute();
    }
}
