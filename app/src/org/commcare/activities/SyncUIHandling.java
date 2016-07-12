package org.commcare.activities;

import org.commcare.interfaces.ConnectorWithResultCallback;
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
    public static void handleSyncResult(ConnectorWithResultCallback activity,
                                        ResultAndError<DataPullTask.PullTaskResult> resultAndErrorMessage,
                                        boolean userTriggeredSync, boolean formsToSend) {
        DataPullTask.PullTaskResult result = resultAndErrorMessage.data;
        String reportSyncLabel = result.getCorrespondingGoogleAnalyticsLabel();
        int reportSyncValue = result.getCorrespondingGoogleAnalyticsValue();

        switch (result) {
            case AUTH_FAILED:
                activity.reportFailure(Localization.get("sync.fail.auth.loggedin"), true);
                break;
            case BAD_DATA:
            case BAD_DATA_REQUIRES_INTERVENTION:
                activity.reportFailure(Localization.get("sync.fail.bad.data"), true);
                break;
            case DOWNLOAD_SUCCESS:
                if (formsToSend) {
                    reportSyncValue = GoogleAnalyticsFields.VALUE_WITH_SEND_FORMS;
                } else {
                    reportSyncValue = GoogleAnalyticsFields.VALUE_JUST_PULL_DATA;
                }
                activity.reportSuccess(Localization.get("sync.success.synced"));
                break;
            case SERVER_ERROR:
                activity.reportFailure(Localization.get("sync.fail.server.error"), true);
                break;
            case UNREACHABLE_HOST:
                activity.reportFailure(Localization.get("sync.fail.bad.network"), true);
                break;
            case CONNECTION_TIMEOUT:
                activity.reportFailure(Localization.get("sync.fail.timeout"), true);
                break;
            case UNKNOWN_FAILURE:
                activity.reportFailure(Localization.get("sync.fail.unknown"), true);
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
        } else if (progressCode == DataPullTask.PROGRESS_DOWNLOADING) {
            activity.updateProgress(Localization.get("sync.process.downloading.progress", new String[]{String.valueOf(update[1])}), DataPullTask.DATA_PULL_TASK_ID);
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

        }
    }
}
