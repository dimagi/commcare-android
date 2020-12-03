package org.commcare.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.UiLoadedListener;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.sync.ProcessAndSendTask;
import org.commcare.tasks.DataPullTask;
import org.commcare.tasks.PullTaskResultReceiver;
import org.commcare.tasks.ResultAndError;
import org.commcare.utils.SyncDetailCalculations;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import androidx.annotation.AnimRes;
import androidx.annotation.LayoutRes;
import androidx.core.view.MenuItemCompat;

public abstract class SyncCapableCommCareActivity<T> extends SessionAwareCommCareActivity<T>
        implements PullTaskResultReceiver {

    protected static final int MENU_SYNC = Menu.FIRST;
    private static final int MENU_GROUP_SYNC_ACTION = Menu.FIRST;

    private static final boolean SUCCESS = true;
    private static final boolean FAIL = false;

    private static final String KEY_LAST_ICON_TRIGGER = "last-icon-trigger";

    protected boolean isSyncUserLaunched = false;
    protected FormAndDataSyncer formAndDataSyncer;

    private SyncIconState syncStateForIcon;
    private SyncIconTrigger lastIconTrigger;
    private MenuItem currentSyncMenuItem;

    private UiLoadedListener uiLoadedListener;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        formAndDataSyncer = new FormAndDataSyncer();
        computeSyncState(savedInstanceState == null ?
                SyncIconTrigger.NO_ANIMATION :
                (SyncIconTrigger)savedInstanceState.getSerializable(KEY_LAST_ICON_TRIGGER));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_LAST_ICON_TRIGGER, lastIconTrigger);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (uiLoadedListener != null) {
            uiLoadedListener.onUiLoaded();
        }
    }

    /**
     * Attempts first to send unsent forms to the server.  If any forms are sent, a sync will be
     * triggered after they are submitted. If no forms are sent, triggers a sync explicitly.
     */
    protected void sendFormsOrSync(boolean userTriggeredSync) {
        startUnsentFormsTask(true, userTriggeredSync);
    }

    protected void startUnsentFormsTask(boolean syncAfterwards, boolean userTriggered) {
        isSyncUserLaunched = userTriggered;
        formAndDataSyncer.startUnsentFormsTask(this, syncAfterwards, userTriggered);
    }

    @Override
    public void handlePullTaskResult(ResultAndError<DataPullTask.PullTaskResult> resultAndError,
                                     boolean userTriggeredSync, boolean formsToSend,
                                     boolean usingRemoteKeyManagement) {
        if (CommCareApplication.instance().isConsumerApp()) {
            return;
        }

        DataPullTask.PullTaskResult result = resultAndError.data;


        switch (result) {
            case EMPTY_URL:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.empty.url"), FAIL);
                break;
            case AUTH_FAILED:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.auth.loggedin"), FAIL);
                break;
            case BAD_DATA:
            case BAD_DATA_REQUIRES_INTERVENTION:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.bad.data"), FAIL);
                break;
            case DOWNLOAD_SUCCESS:
                updateUiAfterDataPullOrSend(Localization.get("sync.success.synced"), SUCCESS);
                break;
            case SERVER_ERROR:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.server.error"), FAIL);
                break;
            case RATE_LIMITED_SERVER_ERROR:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.rate.limited.server.error"), FAIL);
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
            case CANCELLED:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.cancelled"), FAIL);
                break;
            case ENCRYPTION_FAILURE:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.encryption.failure"), FAIL);
                break;
            case SESSION_EXPIRE:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.session.expire"), FAIL);
                break;
            case RECOVERY_FAILURE:
                updateUiAfterDataPullOrSend(Localization.get("sync.fail.recovery.failure"), FAIL);
                break;
            case ACTIONABLE_FAILURE:
                updateUiAfterDataPullOrSend(resultAndError.errorMessage, FAIL);
                break;
            case AUTH_OVER_HTTP:
                updateUiAfterDataPullOrSend(Localization.get("auth.over.http"), FAIL);
                break;
            case CAPTIVE_PORTAL:
                updateUiAfterDataPullOrSend(Localization.get("connection.captive_portal.action"), FAIL);
                break;
        }

        String syncTriggerParam =
                userTriggeredSync ? AnalyticsParamValue.SYNC_TRIGGER_USER : AnalyticsParamValue.SYNC_TRIGGER_AUTO;

        String syncModeParam = formsToSend ? AnalyticsParamValue.SYNC_MODE_SEND_FORMS : AnalyticsParamValue.SYNC_MODE_JUST_PULL_DATA;

        FirebaseAnalyticsUtil.reportSyncResult(result == DataPullTask.PullTaskResult.DOWNLOAD_SUCCESS,
                syncTriggerParam,
                syncModeParam,
                result.analyticsFailureReasonParam);
    }

    @Override
    public void handlePullTaskUpdate(Integer... update) {
        handleSyncUpdate(this, update);
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
                    getSyncProgressMessage(update),
                    Localization.get("sync.processing.title"),
                    DataPullTask.DATA_PULL_TASK_ID);
            activity.updateProgressBar(update[1], update[2], DataPullTask.DATA_PULL_TASK_ID);
        } else if (progressCode == DataPullTask.PROGRESS_RECOVERY_NEEDED) {
            activity.updateProgress(Localization.get("sync.recover.needed"), DataPullTask.DATA_PULL_TASK_ID);
        } else if (progressCode == DataPullTask.PROGRESS_RECOVERY_STARTED) {
            activity.updateProgress(Localization.get("sync.recover.started"), DataPullTask.DATA_PULL_TASK_ID);
        } else if (progressCode == DataPullTask.PROGRESS_SERVER_PROCESSING) {
            activity.updateProgress(
                    getSyncProgressMessage(update),
                    Localization.get("sync.waiting.title"),
                    DataPullTask.DATA_PULL_TASK_ID);
            activity.updateProgressBar(update[1], update[2], DataPullTask.DATA_PULL_TASK_ID);
        }
    }

    private static String getSyncProgressMessage(Integer[] update) {
        Integer numerator = update[1];
        Integer denominator = update[2];
        // If denominator is less than the numerator, use numerator instead to avoid confusion
        denominator = Math.max(numerator, denominator);
        return Localization.get("sync.progress", new String[]{String.valueOf(numerator), String.valueOf(denominator)});
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
        if (success) {
            // Since we know that we just had connectivity, now is a great time to try this
            CommCareApplication.instance().getSession().initHeartbeatLifecycle();
        }
    }

    public void showRateLimitError(boolean userTriggered) {

        if (HiddenPreferences.isRateLimitPopupDisabled() || !userTriggered) {
            handleFormSendResult(Localization.get("form.send.rate.limit.error.toast"), false);
            return;
        }
        String title = Localization.get("form.send.rate.limit.error.title");
        String message = Localization.get("form.send.rate.limit.error.message");
        StandardAlertDialog dialog = StandardAlertDialog.getBasicAlertDialog(this, title,
                message, null);

        dialog.setNegativeButton(Localization.get("rate.limit.error.dialog.do.not.show"), (dialog1, which) -> {
            HiddenPreferences.disableRateLimitPopup(true);
            dismissAlertDialog();
        });
        dialog.setPositiveButton(Localization.get("rate.limit.error.dialog.close"), (dialog1, which) -> {
            dismissAlertDialog();
        });

        showAlertDialog(dialog);
    }

    abstract void updateUiAfterDataPullOrSend(String message, boolean success);

    protected void displayToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void startBlockingForTask(int id) {
        super.startBlockingForTask(id);
        if (isProcessAndSendTaskId(id)) {
            triggerSyncIconRefresh(SyncIconTrigger.ANIMATE_SEND_FORMS);
        } else if (id == DataPullTask.DATA_PULL_TASK_ID) {
            triggerSyncIconRefresh(SyncIconTrigger.ANIMATE_DATA_PULL);
        }
    }

    @Override
    public void stopBlockingForTask(int id) {
        super.stopBlockingForTask(id);
        if (isProcessAndSendTaskId(id) || id == DataPullTask.DATA_PULL_TASK_ID) {
            triggerSyncIconRefresh(SyncIconTrigger.NO_ANIMATION);
        }
    }

    private static boolean isProcessAndSendTaskId(int id) {
        return id == ProcessAndSendTask.SEND_PHASE_ID_NO_DIALOG ||
                id == ProcessAndSendTask.PROCESSING_PHASE_ID_NO_DIALOG ||
                id == ProcessAndSendTask.PROCESSING_PHASE_ID ||
                id == ProcessAndSendTask.SEND_PHASE_ID;
    }

    private void triggerSyncIconRefresh(SyncIconTrigger trigger) {
        if (shouldShowSyncItemInActionBar()) {
            computeSyncState(trigger);
            rebuildOptionsMenu();
        }
    }

    private void computeSyncState(SyncIconTrigger trigger) {
        lastIconTrigger = trigger;
        switch (trigger) {
            case NO_ANIMATION:
                if (SyncDetailCalculations.getNumUnsentForms() > 0) {
                    syncStateForIcon = SyncIconState.FORMS_PENDING;
                } else {
                    syncStateForIcon = SyncIconState.UP_TO_DATE;
                }
                break;
            case ANIMATE_DATA_PULL:
                syncStateForIcon = SyncIconState.PULLING_DATA;
                break;
            case ANIMATE_SEND_FORMS:
                syncStateForIcon = SyncIconState.SENDING_FORMS;
                break;
        }
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
        if (shouldShowSyncItemInActionBar()) {
            currentSyncMenuItem = menu.add(MENU_GROUP_SYNC_ACTION, MENU_SYNC, MENU_SYNC, "Sync");
            currentSyncMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            switch (syncStateForIcon) {
                case PULLING_DATA:
                    addDataPullAnimation(currentSyncMenuItem);
                    break;
                case SENDING_FORMS:
                    addFormSendAnimation(currentSyncMenuItem);
                    break;
                case FORMS_PENDING:
                    currentSyncMenuItem.setIcon(R.drawable.ic_forms_pending_action_bar);
                    break;
                case UP_TO_DATE:
                    currentSyncMenuItem.setIcon(R.drawable.ic_sync_action_bar);
                    break;
            }
        }
    }

    @Override
    public void rebuildOptionsMenu() {
        clearCurrentAnimation(currentSyncMenuItem);
        super.rebuildOptionsMenu();
    }

    private void addDataPullAnimation(MenuItem menuItem) {
        addAnimationToMenuItem(menuItem, R.layout.data_pull_action_view, R.anim.slide_down_repeat);
    }

    private void addFormSendAnimation(MenuItem menuItem) {
        addAnimationToMenuItem(menuItem, R.layout.send_forms_action_view, R.anim.slide_up_repeat);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void addAnimationToMenuItem(MenuItem menuItem, @LayoutRes int layoutResource,
                                        @AnimRes int animationId) {
        LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageView iv = (ImageView)inflater.inflate(layoutResource, null);
        Animation animation = AnimationUtils.loadAnimation(this, animationId);
        iv.startAnimation(animation);
        menuItem.setActionView(iv);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void clearCurrentAnimation(MenuItem item) {
        if (item != null && item.getActionView() != null) {
            item.getActionView().clearAnimation();
            item.setActionView(null);
        }
    }

    /**
     * If true, the action bar of this activity will show an icon or animation at all times
     * indicating the current sync state of the app (1 of either sending forms, pulling data,
     * has pending forms to send, or up-to-date)
     */
    public abstract boolean shouldShowSyncItemInActionBar();

    /**
     * If true, a progress bar will show beneath the action bar during form submission. In order
     * to successfully enable this for an activity, the layout file for that activity must also
     * contain the progress bar element that FormSubmissionProgressBarListener expects.
     */
    public abstract boolean usesSubmissionProgressBar();

    public void setUiLoadedListener(UiLoadedListener listener) {
        this.uiLoadedListener = listener;
    }

    public void removeUiLoadedListener() {
        this.uiLoadedListener = null;
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
                dialog.addCancelButton();
                break;
            case ProcessAndSendTask.PROCESSING_PHASE_ID:
                title = Localization.get("form.entry.processing.title");
                message = Localization.get("form.entry.processing");
                dialog = CustomProgressDialog.newInstance(title, message, taskId);
                dialog.addProgressBar();
                dialog.addCancelButton();
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


    private enum SyncIconState {
        UP_TO_DATE, PULLING_DATA, SENDING_FORMS, FORMS_PENDING
    }

    private enum SyncIconTrigger {
        ANIMATE_DATA_PULL, ANIMATE_SEND_FORMS, NO_ANIMATION
    }

}