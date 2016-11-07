package org.commcare.activities;

import android.os.Bundle;

import org.commcare.CommCareApplication;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.ResultAndError;
import org.javarosa.core.services.locale.Localization;

public abstract class SyncCapableCommCareActivity<R> extends SessionAwareCommCareActivity<R>
        implements PullTaskResultReceiver {

    protected boolean isSyncUserLaunched = false;
    protected FormAndDataSyncer formAndDataSyncer;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        formAndDataSyncer = new FormAndDataSyncer();
    }

    /**
     * Attempts first to send unsent forms to the server.  If any forms are sent, a sync will be
     * triggered after they are submitted. If no forms are sent, triggers a sync explicitly.
     */
    protected void sendFormsOrSync(boolean userTriggeredSync) {
        boolean formsSentToServer = checkAndStartUnsentFormsTask(true, userTriggeredSync);
        if (!formsSentToServer) {
            formAndDataSyncer.syncDataForLoggedInUser(this, false, userTriggeredSync);
        }
    }

    protected boolean checkAndStartUnsentFormsTask(boolean syncAfterwards, boolean userTriggered) {
        isSyncUserLaunched = userTriggered;
        return formAndDataSyncer.checkAndStartUnsentFormsTask(this, syncAfterwards, userTriggered);
    }

    @Override
    public void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndError,
                                     boolean userTriggeredSync, boolean formsToSend) {
        if (CommCareApplication._().isConsumerApp()) {
            return;
        }
        DataPullTask.PullTaskResult result = resultAndError.data;
        String reportSyncLabel = result.getCorrespondingGoogleAnalyticsLabel();
        int reportSyncValue = result.getCorrespondingGoogleAnalyticsValue();

        switch (result) {
            case AUTH_FAILED:
                reportSyncResult(Localization.get("sync.fail.auth.loggedin"), false);
                break;
            case BAD_DATA:
            case BAD_DATA_REQUIRES_INTERVENTION:
                reportSyncResult(Localization.get("sync.fail.bad.data"), false);
                break;
            case DOWNLOAD_SUCCESS:
                if (formsToSend) {
                    reportSyncValue = GoogleAnalyticsFields.VALUE_WITH_SEND_FORMS;
                } else {
                    reportSyncValue = GoogleAnalyticsFields.VALUE_JUST_PULL_DATA;
                }
                reportSyncResult(Localization.get("sync.success.synced"), true);
                break;
            case SERVER_ERROR:
                reportSyncResult(Localization.get("sync.fail.server.error"), false);
                break;
            case UNREACHABLE_HOST:
                reportSyncResult(Localization.get("sync.fail.bad.network"), false);
                break;
            case CONNECTION_TIMEOUT:
                reportSyncResult(Localization.get("sync.fail.timeout"), false);
                break;
            case UNKNOWN_FAILURE:
                reportSyncResult(Localization.get("sync.fail.unknown"), false);
                break;
            case ACTIONABLE_FAILURE:
                reportSyncResult(resultAndError.errorMessage, false);
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
    }

    @Override
    public void handlePullTaskUpdate(Integer... update) {
        handleSyncUpdate(this, update);
    }

    @Override
    public void handlePullTaskError() {
        reportSyncResult(Localization.get("sync.fail.unknown"), false);
    }

    public abstract void reportSyncResult(String message, boolean success);

    public static void handleSyncUpdate(CommCareActivity activity,
                                        Integer... update) {
        int progressCode = update[0];
        if (progressCode == DataPullTask.PROGRESS_STARTED) {
            activity.updateProgress(Localization.get("sync.progress.purge"), DataPullTask.DATA_PULL_TASK_ID);
        } else if (progressCode == DataPullTask.PROGRESS_CLEANED) {
            activity.updateProgress(Localization.get("sync.progress.authing"), DataPullTask.DATA_PULL_TASK_ID);
            activity.updateProgressBarVisibility(false);
        } else if (progressCode == DataPullTask.PROGRESS_AUTHED) {
            activity.updateProgress(Localization.get("sync.progress.downloading"), DataPullTask.DATA_PULL_TASK_ID);
            activity.updateProgressBarVisibility(false);
        } else if (progressCode == DataPullTask.PROGRESS_DOWNLOADING) {
            activity.updateProgress(
                    Localization.get("sync.process.downloading.progress", new String[]{String.valueOf(update[1])}),
                    Localization.get("sync.downloading.title"),
                    DataPullTask.DATA_PULL_TASK_ID);
        } else if (progressCode == DataPullTask.PROGRESS_DOWNLOADING_COMPLETE) {
            activity.hideTaskCancelButton();
        } else if (progressCode == DataPullTask.PROGRESS_PROCESSING) {
            activity.updateProgress(
                    Localization.get("sync.progress", new String[]{String.valueOf(update[1]), String.valueOf(update[2])}),
                    Localization.get("sync.processing.title"),
                    DataPullTask.DATA_PULL_TASK_ID);
            activity.updateProgressBar(update[1], update[2], DataPullTask.DATA_PULL_TASK_ID);
        } else if (progressCode == DataPullTask.PROGRESS_RECOVERY_NEEDED) {
            activity.updateProgress(Localization.get("sync.recover.needed"), DataPullTask.DATA_PULL_TASK_ID);
        } else if (progressCode == DataPullTask.PROGRESS_RECOVERY_STARTED) {
            activity.updateProgress(Localization.get("sync.recover.started"), DataPullTask.DATA_PULL_TASK_ID);
        } else if (progressCode == DataPullTask.PROGRESS_SERVER_PROCESSING) {
            activity.updateProgress(
                    Localization.get("sync.progress", new String[]{String.valueOf(update[1]), String.valueOf(update[2])}),
                    Localization.get("sync.waiting.title"),
                    DataPullTask.DATA_PULL_TASK_ID);
            activity.updateProgressBar(update[1], update[2], DataPullTask.DATA_PULL_TASK_ID);
        }
    }

}
