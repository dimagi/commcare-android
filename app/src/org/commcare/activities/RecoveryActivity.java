package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.FormUploadResult;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

/**
 * @author ctsims
 */
@ManagedUi(R.layout.screen_recovery)
public class RecoveryActivity extends SessionAwareCommCareActivity<RecoveryActivity> {

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
                                displayMessage("Submitting form(s) to the server...");
                            }

                            @Override
                            protected void deliverResult(RecoveryActivity receiver, FormUploadResult result) {
                                if (result == FormUploadResult.PROGRESS_LOGGED_OUT) {

                                    receiver.displayMessage("Log-in expired during send. Please press back and log in again");
                                    return;
                                }

                                int successfulSends = this.getSuccessfulSends();

                                if (result == FormUploadResult.FULL_SUCCESS) {
                                    receiver.displayMessage("Send succesful. All  " + successfulSends + " forms were submitted");
                                } else if (result == FormUploadResult.FAILURE) {
                                    String remainder = successfulSends > 0 ? " Only " + successfulSends + " were submitted" : "";
                                    receiver.displayMessage("There were errors submitting the forms." + remainder);
                                } else if (result == FormUploadResult.TRANSPORT_FAILURE) {
                                    receiver.displayMessage("Unable to contact the remote server.");
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
                                receiver.displayMessage("Error while sending : " + e.getMessage());
                            }

                        };

                mProcess.addSubmissionListener(CommCareApplication.instance().getSession().getListenerForSubmissionNotification());

                mProcess.connect(RecoveryActivity.this);

                //Execute on a true multithreaded chain. We should probably replace all of our calls with this
                //but this is the big one for now.
                mProcess.executeParallel(records);
            }
        });

        btnRecoverApp.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                displayMessage("App recovery is not yet enabled. Please clear user data (After sending all of your forms!) and re-install.");
            }
        });
    }

    private void displayMessage(String text) {
        txtUserMessage.setText(text);
    }

    @Override
    public void startBlockingForTask(int id) {
        btnRecoverApp.setEnabled(false);
        sendForms.setEnabled(false);
    }

    @Override
    public void stopBlockingForTask(int id) {
        updateSendFormsState();
        updateRecoverAppState();
    }

    private void updateRecoverAppState() {
        btnRecoverApp.setEnabled(false);
        if (!CommCareApplication.instance().isStorageAvailable()) {
            appState.setText("app state unavailable.");
            return;
        }

        if (CommCareApplication.instance().getCurrentApp().getAppResourceState() == CommCareApplication.STATE_CORRUPTED) {
            appState.setText("App install is corrupt. Make sure forms are sent before attempting recovery.");
            btnRecoverApp.setEnabled(true);
        } else {
            appState.setText("App is installed and valid");
            btnRecoverApp.setEnabled(false);
        }
    }

    private void updateSendFormsState() {
        sendForms.setEnabled(false);
        if (!CommCareApplication.instance().isStorageAvailable()) {
            txtUnsentAndQuarantineForms.setText("Forms are not available.");
            return;
        }

        try {
            CommCareApplication.instance().getSession();
        } catch (SessionUnavailableException sue) {
            txtUnsentAndQuarantineForms.setText("Couldn't read forms. Not Logged in");
            return;
        }

        SqlStorage<FormRecord> recordStorage = CommCareApplication.instance().getUserStorage(FormRecord.class);
        try {
            StringBuilder sb = new StringBuilder();
            FormRecord[] records = StorageUtils.getUnsentRecordsForCurrentApp(recordStorage);
            if (records.length == 0) {
                sb.append("This device has no unsent forms");
            } else {
                sb.append("There are " + records.length + " unsent form(s)");
                sendForms.setEnabled(true);
            }
            int quarantineForms = StorageUtils.getNumQuarantinedForms();
            if (quarantineForms != 0) {
                sb.append(" and " + quarantineForms + " quarantine form(s)");
            }
            txtUnsentAndQuarantineForms.setText(sb.toString());
        } catch (Exception e) {
            Logger.exception("Encountered exception during recovery attempt " + e.getMessage(), e);
            txtUnsentAndQuarantineForms.setText("Couldn't read forms. Error : " + e.getMessage());
        }
    }
}
