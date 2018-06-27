package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.ServerUrls;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.tasks.ResourceRecoveryTask;
import org.commcare.util.CommCarePlatform;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.ConsumerAppsUtil;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.StringUtils;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * @author ctsims
 */
@ManagedUi(R.layout.screen_recovery)
public class RecoveryActivity extends SessionAwareCommCareActivity<RecoveryActivity> {

    private static final int RECOVERY_TASK = 10000;
    @UiElement(R.id.screen_recovery_unsent_message)
    TextView txtUnsentAndQuarantineForms;

    @UiElement(R.id.screen_recovery_unsent_button)
    Button sendForms;

    @UiElement(R.id.screen_recovery_app_install_message)
    TextView appState;
    @UiElement(R.id.screen_recovery_app_install_button)
    Button btnRecoverApp;

    @UiElement(R.id.screen_recovery_progress)
    TextView txtUserMessage;


    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
        super.onCreateSessionSafe(savedInstanceState);

        if (savedInstanceState == null) {
            // launching activity, not just changing orientation
            CommCareUtil.triggerLogSubmission(this);
            updateSendFormsState();
            updateRecoverAppState();
        }

        sendForms.setOnClickListener(new OnClickListener() {

            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                txtUserMessage.setVisibility(View.GONE);
                FormRecord[] records = StorageUtils.getUnsentRecordsForCurrentApp(
                        CommCareApplication.instance().getUserStorage(FormRecord.class));
                SharedPreferences settings = CommCareApplication.instance().getCurrentApp().getAppPreferences();

                ProcessAndSendTask<RecoveryActivity> mProcess =
                        new ProcessAndSendTask<RecoveryActivity>(RecoveryActivity.this,
                                settings.getString(ServerUrls.PREFS_SUBMISSION_URL_KEY,
                                        RecoveryActivity.this.getString(R.string.PostURL)), true) {

                            @Override
                            protected void onPreExecute() {
                                super.onPreExecute();
                                displayMessage(StringUtils.getStringRobust(RecoveryActivity.this, R.string.recovery_forms_send_progress));
                            }

                            @Override
                            protected void deliverResult(RecoveryActivity receiver, FormUploadResult result) {
                                if (result == FormUploadResult.PROGRESS_LOGGED_OUT) {
                                    receiver.displayMessage(StringUtils.getStringRobust(RecoveryActivity.this, R.string.recovery_forms_send_session_expired));
                                    return;
                                }

                                int successfulSends = this.getSuccessfulSends();

                                if (result == FormUploadResult.FULL_SUCCESS) {
                                    receiver.displayMessage(StringUtils.getStringRobust(
                                            RecoveryActivity.this,
                                            R.string.recovery_forms_send_successful,
                                            String.valueOf(successfulSends)));
                                } else if (result == FormUploadResult.FAILURE) {
                                    String failureMessage = successfulSends > 0 ?
                                            StringUtils.getStringRobust(
                                                    RecoveryActivity.this,
                                                    R.string.recovery_forms_send_failure) :
                                            StringUtils.getStringRobust(
                                                    RecoveryActivity.this,
                                                    R.string.recovery_forms_send_parital_success,
                                                    String.valueOf(successfulSends));
                                    receiver.displayMessage(failureMessage);
                                } else if (result == FormUploadResult.TRANSPORT_FAILURE) {
                                    receiver.displayMessage(StringUtils.getStringRobust(RecoveryActivity.this, R.string.recovery_forms_send_network_error));
                                } else if (result == FormUploadResult.RECORD_FAILURE) {
                                    receiver.displayMessage(Localization.get("sync.fail.individual"));
                                } else if (result == FormUploadResult.AUTH_OVER_HTTP) {
                                    receiver.displayMessage(Localization.get("auth.over.http"));
                                }
                            }

                            @Override
                            protected void deliverUpdate(RecoveryActivity receiver, Long... update) {
                                //we don't need to deliver updates here, it happens on the notification bar
                            }

                            @Override
                            protected void deliverError(RecoveryActivity receiver, Exception e) {
                                Logger.exception("Error in recovery form send: " + ForceCloseLogger.getStackTrace(e), e);
                                receiver.displayMessage(StringUtils.getStringRobust(receiver, R.string.recovery_forms_send_error) + ": " + e.getMessage());
                            }

                        };

                mProcess.addSubmissionListener(CommCareApplication.instance().getSession().getListenerForSubmissionNotification());

                mProcess.connect(RecoveryActivity.this);

                //Execute on a true multithreaded chain. We should probably replace all of our calls with this
                //but this is the big one for now.
                mProcess.executeParallel(records);
            }
        });

        btnRecoverApp.setOnClickListener(v -> {
            txtUserMessage.setVisibility(View.GONE);
            attemptRecovery();
        });
    }

    private void attemptRecovery() {
        AndroidCommCarePlatform platform = CommCareApplication.instance().getCommCarePlatform();
        ResourceTable global = platform.getGlobalResourceTable();
        if (!global.getMissingResources().isEmpty()) {
            int total = global.getMissingResources().size();
            ResourceRecoveryTask<RecoveryActivity> task =
                    new ResourceRecoveryTask<RecoveryActivity>(RECOVERY_TASK) {

                        @Override
                        protected void deliverResult(RecoveryActivity recoveryActivity, Boolean success) {
                            if (success) {
                                // Try Reinitializing and check if nothing is missing this time, if yes then auto recover again untill
                                // there are no missing resources
                                CommCareApplication.instance().initializeAppResources(CommCareApplication.instance().getCurrentApp());
                                attemptRecovery();
                            }
                        }

                        @Override
                        protected void deliverUpdate(RecoveryActivity recoveryActivity, Integer... update) {
                            int done = update[0];
                            recoveryActivity.updateProgress(
                                    StringUtils.getStringRobust(recoveryActivity, R.string.recovery_resource_progress,
                                            new String[]{String.valueOf(done), String.valueOf(total)}),
                                    RECOVERY_TASK);
                            updateProgressBar(done, total, RECOVERY_TASK);
                        }

                        @Override
                        protected void deliverError(RecoveryActivity recoveryActivity, Exception e) {
                            Logger.exception("Error while recovering missing resources " + ForceCloseLogger.getStackTrace(e), e);
                            recoveryActivity.displayMessage(StringUtils.getStringRobust(recoveryActivity, R.string.recovery_resource_error) + ": " + e.getMessage());
                        }
                    };
            task.connect(this);
            task.executeParallel();
        } else {
            updateRecoverAppState();
            displayMessage(StringUtils.getStringRobust(this, R.string.recovery_resource_success));
        }
    }

    private void displayMessage(String text) {
        txtUserMessage.setVisibility(View.VISIBLE);
        txtUserMessage.setText(text);
    }

    @Override
    public void startBlockingForTask(int id) {
        super.startBlockingForTask(id);
        btnRecoverApp.setEnabled(false);
        sendForms.setEnabled(false);
    }

    @Override
    public void stopBlockingForTask(int id) {
        super.stopBlockingForTask(id);
        updateSendFormsState();
        updateRecoverAppState();
    }

    private void updateRecoverAppState() {
        btnRecoverApp.setEnabled(false);
        if (!CommCareApplication.instance().isStorageAvailable()) {
            appState.setText(StringUtils.getStringRobust(this, R.string.recovery_app_state_unavailable));
            return;
        }

        if (CommCareApplication.instance().getCurrentApp().getAppResourceState() == CommCareApplication.STATE_CORRUPTED) {
            appState.setText(StringUtils.getStringRobust(this, R.string.recovery_app_state_corrupt));
            btnRecoverApp.setEnabled(true);
        } else {
            appState.setText(StringUtils.getStringRobust(this, R.string.recovery_app_state_valid));
            btnRecoverApp.setEnabled(false);
        }
    }

    private void updateSendFormsState() {
        sendForms.setEnabled(false);
        if (!CommCareApplication.instance().isStorageAvailable()) {
            txtUnsentAndQuarantineForms.setText(StringUtils.getStringRobust(this, R.string.recovery_forms_state_unavailable));
            return;
        }

        try {
            CommCareApplication.instance().getSession();
        } catch (SessionUnavailableException sue) {
            txtUnsentAndQuarantineForms.setText(StringUtils.getStringRobust(this, R.string.recovery_forms_state_logged_out));
            return;
        }

        SqlStorage<FormRecord> recordStorage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        try {
            int unsentForms = StorageUtils.getUnsentRecordsForCurrentApp(recordStorage).length;
            int quarantineForms = StorageUtils.getNumQuarantinedForms();
            String formsStateMessage = StringUtils.getStringRobust(this,
                    R.string.recovery_forms_state_number_unsent_quarantine,
                    new String[]{String.valueOf(unsentForms), String.valueOf(quarantineForms)});
            sendForms.setEnabled(unsentForms > 0);
            txtUnsentAndQuarantineForms.setText(formsStateMessage);
        } catch (Exception e) {
            Logger.exception("Encountered exception during recovery attempt " + e.getMessage(), e);
            txtUnsentAndQuarantineForms.setText(
                    StringUtils.getStringRobust(this, R.string.recovery_forms_state_error) + ": " + e.getMessage());
        }
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId == RECOVERY_TASK) {
            CustomProgressDialog dialog =
                    CustomProgressDialog.newInstance(
                            StringUtils.getStringRobust(this, R.string.recovering_resources_title),
                            StringUtils.getStringRobust(this, R.string.recovering_resources_progress),
                            taskId);
            dialog.addProgressBar();
            dialog.addCancelButton();
            return dialog;
        }
        return null;
    }
}
