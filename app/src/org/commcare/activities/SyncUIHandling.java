package org.commcare.activities;

import org.commcare.interfaces.SyncCapableCommCareActivity;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ResultAndError;
import org.javarosa.core.services.locale.Localization;

/**
 * Default sync task result handling logic.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SyncUIHandling {
    public static void handleSyncResult(SyncCapableCommCareActivity activity,
                                        ResultAndError<DataPullTask.PullTaskResult> resultAndErrorMessage,
                                        boolean userTriggeredSync, boolean formsToSend) {
        DataPullTask.PullTaskResult result = resultAndErrorMessage.data;
        String reportSyncLabel = result.getCorrespondingGoogleAnalyticsLabel();
        int reportSyncValue = result.getCorrespondingGoogleAnalyticsValue();

        switch (result) {
            case AUTH_FAILED:
                activity.reportSyncResult(Localization.get("sync.fail.auth.loggedin"), false);
                break;
            case BAD_DATA:
            case BAD_DATA_REQUIRES_INTERVENTION:
                activity.reportSyncResult(Localization.get("sync.fail.bad.data"), false);
                break;
            case DOWNLOAD_SUCCESS:
                if (formsToSend) {
                    reportSyncValue = GoogleAnalyticsFields.VALUE_WITH_SEND_FORMS;
                } else {
                    reportSyncValue = GoogleAnalyticsFields.VALUE_JUST_PULL_DATA;
                }
                activity.reportSyncResult(Localization.get("sync.success.synced"), true);
                break;
            case SERVER_ERROR:
                activity.reportSyncResult(Localization.get("sync.fail.server.error"), false);
                break;
            case UNREACHABLE_HOST:
                activity.reportSyncResult(Localization.get("sync.fail.bad.network"), false);
                break;
            case CONNECTION_TIMEOUT:
                activity.reportSyncResult(Localization.get("sync.fail.timeout"), false);
                break;
            case UNKNOWN_FAILURE:
                activity.reportSyncResult(Localization.get("sync.fail.unknown"), false);
                break;
            case ACTIONABLE_FAILURE:
                activity.reportSyncResult(resultAndErrorMessage.errorMessage, false);
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
