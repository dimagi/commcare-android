package org.commcare.activities;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.SyncDetailCalculations;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;

public abstract class SyncCapableCommCareActivity<T> extends SessionAwareCommCareActivity<T>
        implements PullTaskResultReceiver {

    protected static final int MENU_SYNC = Menu.FIRST;
    private static final int MENU_GROUP_SYNC_ACTION = Menu.FIRST;

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
        if (CommCareApplication.instance().isConsumerApp()) {
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

    public void reportSyncResult(String message, boolean success) {
        if (shouldShowSyncItemInActionBar()) {
            if (success) {
                rebuildOptionsMenu();
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        CustomProgressDialog dialog;
        switch (taskId) {
            case ProcessAndSendTask.SEND_PHASE_ID:
                title = Localization.get("sync.progress.submitting.title");
                message = Localization.get("sync.progress.submitting");
                dialog = CustomProgressDialog.newInstance(title, message, taskId);
                break;
            case ProcessAndSendTask.PROCESSING_PHASE_ID:
                title = Localization.get("form.entry.processing.title");
                message = Localization.get("form.entry.processing");
                dialog = CustomProgressDialog.newInstance(title, message, taskId);
                dialog.addProgressBar();
                break;
            case DataPullTask.DATA_PULL_TASK_ID:
                title = Localization.get("sync.communicating.title");
                message = Localization.get("sync.progress.purge");
                dialog = CustomProgressDialog.newInstance(title, message, taskId);
                if (isSyncUserLaunched) {
                    // allow users to cancel syncs that they launched
                    dialog.addCancelButton();
                }
                isSyncUserLaunched = false;
                break;
            default:
                return null;
        }
        return dialog;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SYNC) {
            sendFormsOrSync(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        addSyncItemToActionBar(menu);
        return true;
    }

    public void addSyncItemToActionBar(Menu menu) {
        if (shouldShowSyncItemInActionBar() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            MenuItem item = menu.add(MENU_GROUP_SYNC_ACTION, MENU_SYNC, MENU_SYNC, "Sync");
            Drawable syncDrawable =
                    getResources().getDrawable(R.drawable.ic_sync_action_bar);
            int numUnsentForms = SyncDetailCalculations.getNumUnsentForms();
            if (numUnsentForms > 0) {
                syncDrawable.setColorFilter(new PorterDuffColorFilter(
                        getResources().getColor(R.color.cc_attention_negative_color),
                        PorterDuff.Mode.MULTIPLY));
            } else {
                syncDrawable.setColorFilter(null);
            }
            item.setIcon(syncDrawable);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    public abstract boolean shouldShowSyncItemInActionBar();

}