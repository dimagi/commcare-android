package org.commcare.activities;

import android.app.ActionBar;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.dalvik.R;
import org.commcare.preferences.ServerUrls;
import org.commcare.resources.model.ResourceTable;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.ResourceRecoveryTask;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.ConnectivityStatus;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.StringUtils;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * @author ctsims
 */
@ManagedUi(R.layout.screen_recovery)
public class RecoveryActivity extends SessionAwareCommCareActivity<RecoveryActivity> {

    private static final int RECOVERY_TASK = 10000;
    private static final int MENU_APP_MANAGER = Menu.FIRST;

    @UiElement(R.id.recovery_title)
    TextView titleTv;

    @UiElement(R.id.recovery_progress)
    ProgressBar loadingIndicator;

    @UiElement(R.id.recovery_status)
    TextView statusTv;

    @UiElement(R.id.app_manager_button)
    Button appManagerBt;

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);
        if (savedInstanceState == null) {
            // launching activity, not just changing orientation
            CommCareUtil.triggerLogSubmission(this);
            sendForms();
        }

        appManagerBt.setOnClickListener(v -> {
            launchAppManager();
        });
    }

    private void sendForms() {

        if (!isNetworkAvaialable() || !isStorageAvailable()) {
            return;
        }

        loadingIndicator.setVisibility(View.VISIBLE);
        updateStatus(R.string.recovery_forms_send_progress);

        FormRecord[] records = StorageUtils.getUnsentRecordsForCurrentApp(
                CommCareApplication.instance().getUserStorage(FormRecord.class));
        SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();

        ProcessAndSendTask<RecoveryActivity> mProcess =
                new ProcessAndSendTask<RecoveryActivity>(RecoveryActivity.this,
                        settings.getString(ServerUrls.PREFS_SUBMISSION_URL_KEY,
                                RecoveryActivity.this.getString(R.string.PostURL)), true) {


                    @Override
                    protected void deliverResult(RecoveryActivity receiver, FormUploadResult result) {
                        if (result == FormUploadResult.PROGRESS_LOGGED_OUT) {
                            receiver.updateStatus(R.string.recovery_forms_send_session_expired);
                            receiver.relaunch();
                            return;
                        }

                        receiver.loadingIndicator.setVisibility(View.INVISIBLE);

                        int successfulSends = this.getSuccessfulSends();

                        if (result == FormUploadResult.FULL_SUCCESS) {
                            receiver.updateStatus(StringUtils.getStringRobust(
                                    RecoveryActivity.this,
                                    R.string.recovery_forms_send_successful,
                                    String.valueOf(successfulSends)));
                            receiver.attemptRecovery();
                        } else if (result == FormUploadResult.FAILURE) {
                            String failureMessage = successfulSends > 0 ?
                                    StringUtils.getStringRobust(
                                            RecoveryActivity.this,
                                            R.string.recovery_forms_send_failure) :
                                    StringUtils.getStringRobust(
                                            RecoveryActivity.this,
                                            R.string.recovery_forms_send_parital_success,
                                            String.valueOf(successfulSends));
                            receiver.updateStatus(failureMessage);
                        } else if (result == FormUploadResult.TRANSPORT_FAILURE) {
                            receiver.updateStatus(R.string.recovery_forms_send_network_error);
                        } else if (result == FormUploadResult.RECORD_FAILURE) {
                            receiver.updateStatus(Localization.get("sync.fail.individual"));
                        } else if (result == FormUploadResult.AUTH_OVER_HTTP) {
                            receiver.updateStatus(Localization.get("auth.over.http"));
                        }
                    }

                    @Override
                    protected void deliverUpdate(RecoveryActivity receiver, Long... update) {
                        //we don't need to deliver updates here, it happens on the notification bar
                    }

                    @Override
                    protected void deliverError(RecoveryActivity receiver, Exception e) {
                        Logger.exception("Error in recovery form send: " + ForceCloseLogger.getStackTrace(e), e);
                        receiver.updateStatus(StringUtils.getStringRobust(receiver, R.string.recovery_forms_send_error) + ": " + e.getMessage());
                        receiver.loadingIndicator.setVisibility(View.INVISIBLE);
                    }

                };

        mProcess.addSubmissionListener(CommCareApplication.instance().getSession().getListenerForSubmissionNotification());
        mProcess.connect(this);

        //Execute on a true multithreaded chain. We should probably replace all of our calls with this
        //but this is the big one for now.
        mProcess.executeParallel(records);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        setUpActionBar();
        menu.add(0, MENU_APP_MANAGER, 1, Localization.get("login.menu.app.manager"));
        return super.onCreateOptionsMenu(menu);
    }

    private void setUpActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setTitle(getActivityTitle());
        }
    }

    private boolean isNetworkAvaialable() {
        boolean network = ConnectivityStatus.isNetworkAvailable(this);
        if (!network) {
            loadingIndicator.setVisibility(View.INVISIBLE);
            updateStatus(StringUtils.getStringRobust(this, R.string.recovery_network_unavailable));
        }
        return network;
    }

    public void attemptRecovery() {
        appManagerBt.setVisibility(View.INVISIBLE);
        loadingIndicator.setVisibility(View.VISIBLE);

        CommCareApplication commCareApplication = CommCareApplication.instance();
        // Try to reinitialize to refresh the list of missing resources
        commCareApplication.initializeAppResources(commCareApplication.getCurrentApp());
        AndroidCommCarePlatform platform = commCareApplication.getCommCarePlatform();
        ResourceTable global = platform.getGlobalResourceTable();
        if (!global.getMissingResources().isEmpty()) {
            ResourceRecoveryTask task = ResourceRecoveryTask.getInstance();
            task.connect(this);
            task.executeParallel();
        } else {
            if (isAppCorrupt()) {
                onRecoveryFailure(getLocalizedString(R.string.recovery_error_unknown));
            } else {
                updateStatus(R.string.recovery_success);
                loadingIndicator.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void onRecoveryFailure(int messageResource) {
        onRecoveryFailure(getLocalizedString(messageResource));
    }

    public void onRecoveryFailure(String message) {
        updateStatus(message);
        appManagerBt.setVisibility(View.VISIBLE);
        loadingIndicator.setVisibility(View.INVISIBLE);
    }


    private void updateStatus(int resource) {
        updateStatus(StringUtils.getStringRobust(this, resource));
    }

    public void updateStatus(String text) {
        statusTv.setVisibility(View.VISIBLE);
        statusTv.setText(text);
    }

    @Override
    public void startBlockingForTask(int id) {
        super.startBlockingForTask(id);
    }

    @Override
    public void stopBlockingForTask(int id) {
        super.stopBlockingForTask(id);
    }

    private static boolean isAppCorrupt() {
        return CommCareApplication.instance().getCurrentApp().getAppResourceState() == CommCareApplication.STATE_CORRUPTED;
    }

    @Override
    public void onBackPressed() {
        // If app is no longer corrupt, launch home screen again
        if (!isAppCorrupt()) {
            relaunch();
        } else {
            super.onBackPressed();
        }
    }

    private void relaunch() {
        Intent i = new Intent(this, DispatchActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private boolean isStorageAvailable() {
        if (!CommCareApplication.instance().isStorageAvailable()) {
            updateStatus(getLocalizedString(R.string.recovery_forms_state_unavailable));
            return false;
        }
        return true;
    }

    @Override
    protected String getActivityTitle() {
        return StringUtils.getStringRobust(this, R.string.recovery_mode_title);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_APP_MANAGER:
                launchAppManager();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void launchAppManager() {
        Intent i = new Intent(this, AppManagerActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    public void stopLoading() {
        loadingIndicator.setVisibility(View.INVISIBLE);
    }
}
