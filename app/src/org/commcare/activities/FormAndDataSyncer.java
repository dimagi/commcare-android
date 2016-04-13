package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.widget.Toast;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.models.database.global.models.ApplicationRecord;
import org.commcare.models.database.user.models.FormRecord;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.ResultAndError;
import org.commcare.tasks.UpdatePropertiesTask;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;

/**
 * Processes and submits forms and syncs data with server
 */
public class FormAndDataSyncer {
    private final CommCareHomeActivity activity;

    public FormAndDataSyncer(CommCareHomeActivity activity) {
        this.activity = activity;
    }

    @SuppressLint("NewApi")
    public void processAndSendForms(FormRecord[] records,
                                    final boolean syncAfterwards,
                                    final boolean userTriggered) {

        ProcessAndSendTask<CommCareHomeActivity> mProcess = new ProcessAndSendTask<CommCareHomeActivity>(
                activity,
                getFormPostURL(activity),
                syncAfterwards) {

            @Override
            protected void deliverResult(CommCareHomeActivity receiver, Integer result) {
                if (result == ProcessAndSendTask.PROGRESS_LOGGED_OUT) {
                    receiver.finish();
                    return;
                }
                activity.getUIController().refreshView();

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
                    receiver.displayMessage(Localization.get("sync.fail.unsent"), true);
                }
            }

            @Override
            protected void deliverUpdate(CommCareHomeActivity receiver, Long... update) {
            }

            @Override
            protected void deliverError(CommCareHomeActivity receiver, Exception e) {
                receiver.displayMessage(Localization.get("sync.fail.unsent"), true);
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
                    activity.displayMessage(Localization.get("main.sync.demo.has.forms"), true, true);
                } else {
                    activity.displayMessage(Localization.get("main.sync.demo.no.forms"), true, true);
                }
            }
            return;
        }

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        DataPullTask<CommCareHomeActivity> dataPullTask = new DataPullTask<CommCareHomeActivity>(
                u.getUsername(),
                u.getCachedPwd(),
                prefs.getString(CommCarePreferences.PREFS_DATA_SERVER_KEY,
                        activity.getString(R.string.ota_restore_url)),
                activity) {

            @Override
            protected void deliverResult(CommCareHomeActivity receiver, ResultAndError<PullTaskResult> resultAndErrorMessage) {
                receiver.getUIController().refreshView();

                PullTaskResult result = resultAndErrorMessage.data;
                String reportSyncLabel = result.getCorrespondingGoogleAnalyticsLabel();
                int reportSyncValue = result.getCorrespondingGoogleAnalyticsValue();

                //TODO: SHARES _A LOT_ with login activity. Unify into service
                switch (result) {
                    case AUTH_FAILED:
                        receiver.displayMessage(Localization.get("sync.fail.auth.loggedin"), true);
                        break;
                    case BAD_DATA:
                    case BAD_DATA_REQUIRES_INTERVENTION:
                        receiver.displayMessage(Localization.get("sync.fail.bad.data"), true);
                        break;
                    case DOWNLOAD_SUCCESS:
                        if (formsToSend) {
                            reportSyncValue = GoogleAnalyticsFields.VALUE_WITH_SEND_FORMS;
                        } else {
                            reportSyncValue = GoogleAnalyticsFields.VALUE_JUST_PULL_DATA;
                        }
                        receiver.displayMessage(Localization.get("sync.success.synced"));

                        refreshPropertiesFromServer(receiver);
                        break;
                    case SERVER_ERROR:
                        receiver.displayMessage(Localization.get("sync.fail.server.error"));
                        break;
                    case UNREACHABLE_HOST:
                        receiver.displayMessage(Localization.get("sync.fail.bad.network"), true);
                        break;
                    case CONNECTION_TIMEOUT:
                        receiver.displayMessage(Localization.get("sync.fail.timeout"), true);
                        break;
                    case UNKNOWN_FAILURE:
                        receiver.displayMessage(Localization.get("sync.fail.unknown"), true);
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
            protected void deliverUpdate(CommCareHomeActivity receiver, Integer... update) {
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
            protected void deliverError(CommCareHomeActivity receiver,
                                        Exception e) {
                receiver.displayMessage(Localization.get("sync.fail.unknown"), true);
            }
        };

        dataPullTask.connect(activity);
        dataPullTask.execute();
    }

    public static void refreshPropertiesFromServer(CommCareActivity receiver) {
        refreshPropertiesFromServer(receiver, null, -1);
    }

    public static void refreshPropertiesFromServer(CommCareActivity receiver, ApplicationRecord record) {
        ArrayList<ApplicationRecord> list = new ArrayList<>();
        list.add(record);
        refreshPropertiesFromServer(receiver, list, 0);
    }

    private static void refreshPropertiesFromServer(CommCareActivity receiver,
                                                   final ArrayList<ApplicationRecord> appsToRefresh,
                                                   final int index) {

        UpdatePropertiesTask<CommCareActivity> updatePropertiesTask = new UpdatePropertiesTask<CommCareActivity>() {

            @Override
            protected void deliverResult(CommCareActivity receiver, UpdatePropertiesResult result) {
                String appDisplayName;
                if (appsToRefresh != null) {
                    appDisplayName = appsToRefresh.get(index).getDisplayName();
                } else {
                    appDisplayName = CommCareApplication._().getCurrentApp().getAppRecord().getDisplayName();
                }

                if (result == UpdatePropertiesResult.SUCCESS) {
                    Toast.makeText(receiver,
                            Localization.get("properties.update.success.toast", new String[]{appDisplayName}),
                            Toast.LENGTH_LONG)
                            .show();
                } else {
                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(result));
                    Toast.makeText(receiver,
                            Localization.get("notification.for.details.wrapper",
                                    new String[]{Localization.get("properties.update.error.toast",
                                            new String[]{appDisplayName})}),
                            Toast.LENGTH_LONG)
                            .show();
                }

                if (appsToRefresh != null && index < appsToRefresh.size()-1) {
                    refreshPropertiesFromServer(receiver, appsToRefresh, index+1);
                }
            }

            @Override
            protected void deliverUpdate(CommCareActivity receiver, Void... update) {
            }

            @Override
            protected void deliverError(CommCareActivity receiver, Exception e) {
            }
        };

        updatePropertiesTask.connect(receiver);
        if (appsToRefresh != null) {
            updatePropertiesTask.execute(appsToRefresh.get(index));
        } else {
            updatePropertiesTask.execute();
        }
    }

    public static void refreshPropertiesForAllInstalledApps(CommCareActivity receiver) {
        refreshPropertiesFromServer(receiver, CommCareApplication._().getInstalledAppRecords(), 0);
    }
}
