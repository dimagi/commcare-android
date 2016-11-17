package org.commcare.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.AnimRes;
import android.support.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.tasks.CommCareSyncState;
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

    private static final boolean SUCCESS = true;
    private static final boolean FAIL = false;

    private static final int TRIGGER_START_DATA_PULL = 0;
    private static final int TRIGGER_END_DATA_PULL = 1;
    private static final int TRIGGER_START_SEND_FORMS = 2;
    private static final int TRIGGER_END_SEND_FORMS = 3;
    private static final int TRIGGER_NONE = 4;

    protected boolean isSyncUserLaunched = false;
    protected FormAndDataSyncer formAndDataSyncer;

    private CommCareSyncState syncStateForIcon;

    @Override
    protected void onCreateSessionSafe(Bundle savedInstanceState) {
        formAndDataSyncer = new FormAndDataSyncer();
        computeSyncState(TRIGGER_NONE);
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
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.auth.loggedin"), FAIL);
                break;
            case BAD_DATA:
            case BAD_DATA_REQUIRES_INTERVENTION:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.bad.data"), FAIL);
                break;
            case DOWNLOAD_SUCCESS:
                if (formsToSend) {
                    reportSyncValue = GoogleAnalyticsFields.VALUE_WITH_SEND_FORMS;
                } else {
                    reportSyncValue = GoogleAnalyticsFields.VALUE_JUST_PULL_DATA;
                }
                updateUiAfterDataPullOrSend(Localization.get("sync.success.synced"), SUCCESS);
                break;
            case SERVER_ERROR:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.server.error"), FAIL);
                break;
            case UNREACHABLE_HOST:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.bad.network"), FAIL);
                break;
            case CONNECTION_TIMEOUT:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.timeout"), FAIL);
                break;
            case UNKNOWN_FAILURE:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.unknown"), FAIL);
                break;
            case ACTIONABLE_FAILURE:
                updateUiAfterDataPullOrSend(resultAndError.errorMessage, FAIL);
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
        updateUiAfterDataPullOrSend(Localization.get("sync.fail.unknown"), FAIL);
    }

    public void handleSyncNotAttempted(String message) {
        displayToast(message);
    }

    public void handleFormSendResult(String message, boolean success) {
        updateUiAfterDataPullOrSend(message, success);
    }

    abstract void updateUiAfterDataPullOrSend(String message, boolean success);

    protected void displayToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void startBlockingForTask(int id) {
        super.startBlockingForTask(id);
        if (id == ProcessAndSendTask.SEND_PHASE_ID_NO_DIALOG ||
                id == ProcessAndSendTask.PROCESSING_PHASE_ID_NO_DIALOG) {
            refreshSyncIcon(TRIGGER_START_SEND_FORMS);
        } else if (id == DataPullTask.DATA_PULL_TASK_ID) {
            refreshSyncIcon(TRIGGER_START_DATA_PULL);
        }
    }

    @Override
    public void stopBlockingForTask(int id) {
        super.stopBlockingForTask(id);
        if (id == ProcessAndSendTask.SEND_PHASE_ID_NO_DIALOG ||
                id == ProcessAndSendTask.PROCESSING_PHASE_ID_NO_DIALOG) {
            refreshSyncIcon(TRIGGER_END_SEND_FORMS);
        } else if (id == DataPullTask.DATA_PULL_TASK_ID) {
            refreshSyncIcon(TRIGGER_END_DATA_PULL);
        }
    }

    private void refreshSyncIcon(int trigger) {
        if (shouldShowSyncItemInActionBar()) {
            computeSyncState(trigger);
            rebuildOptionsMenu();
        }
    }

    private void computeSyncState(int trigger) {
        switch(trigger) {
            case TRIGGER_END_DATA_PULL:
            case TRIGGER_END_SEND_FORMS:
            case TRIGGER_NONE:
                if (SyncDetailCalculations.getNumUnsentForms() > 0) {
                    syncStateForIcon = CommCareSyncState.FORMS_PENDING;
                } else {
                    syncStateForIcon = CommCareSyncState.UP_TO_DATE;
                }
                break;
            case TRIGGER_START_DATA_PULL:
                syncStateForIcon = CommCareSyncState.PULLING_DATA;
                break;
            case TRIGGER_START_SEND_FORMS:
                syncStateForIcon = CommCareSyncState.SENDING_FORMS;
                break;
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

    private void addSyncItemToActionBar(Menu menu) {
        if (shouldShowSyncItemInActionBar() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            MenuItem item = menu.add(MENU_GROUP_SYNC_ACTION, MENU_SYNC, MENU_SYNC, "Sync");
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            switch(syncStateForIcon) {
                case PULLING_DATA:
                    addDataPullAnimation(item);
                    break;
                case SENDING_FORMS:
                    addFormSendAnimation(item);
                    break;
                case FORMS_PENDING:
                    item.setIcon(R.drawable.ic_forms_pending_action_bar);
                    break;
                case UP_TO_DATE:
                    item.setIcon(R.drawable.ic_sync_action_bar);
                    break;
            }
        }
    }

    private void addDataPullAnimation(MenuItem menuItem) {

    }

    private void addFormSendAnimation(MenuItem menuItem) {
        //addAnimationToMenuItem(menuItem, R.layout.data_pull_action_view, R.anim.fade_in);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void addAnimationToMenuItem(MenuItem menuItem, @LayoutRes int layoutResource,
                                        @AnimRes int animationId) {
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageView iv = (ImageView)inflater.inflate(layoutResource, null);
        Animation animation = AnimationUtils.loadAnimation(this, animationId);
        animation.setRepeatCount(Animation.INFINITE);
        iv.startAnimation(animation);
        menuItem.setActionView(iv);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void clearCurrentAnimations(MenuItem item) {
        item.getActionView().clearAnimation();
        item.setActionView(null);
    }

    public abstract boolean shouldShowSyncItemInActionBar();

    public boolean usesSubmissionProgressBar() {
        return false;
    }

}