package org.commcare.activities;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.android.logging.ForceCloseLogger;
import org.commcare.dalvik.R;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.tasks.ProcessAndSendTask;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StorageUtils;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.javarosa.core.services.Logger;

/**
 * @author ctsims
 */
@ManagedUi(R.layout.screen_recovery)
public class RecoveryActivity extends SessionAwareCommCareActivity<RecoveryActivity> {

    @UiElement(R.id.screen_recovery_unsent_message)
    TextView txtUnsentForms;

    @UiElement(R.id.screen_recovery_unsent_button)
    Button sendForms;

    @UiElement(R.id.screen_recovery_app_install_message)
    TextView appState;
    @UiElement(R.id.screen_recovery_app_install_button)
    Button btnRecoverApp;

    @UiElement(R.id.screen_recovery_progress)
    TextView txtUserMessage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            // launching activity, not just changing orientation
            updateSendFormsState();
            updateRecoverAppState();
        }

        sendForms.setOnClickListener(new OnClickListener() {

            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                FormRecord[] records = StorageUtils.getUnsentRecords(CommCareApplication._().getUserStorage(FormRecord.class));
                SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();

                ProcessAndSendTask<RecoveryActivity> mProcess =
                        new ProcessAndSendTask<RecoveryActivity>(RecoveryActivity.this,
                                settings.getString(CommCareServerPreferences.PREFS_SUBMISSION_URL_KEY,
                                        RecoveryActivity.this.getString(R.string.PostURL)), true) {

                            @Override
                            protected void onPreExecute() {
                                super.onPreExecute();
                                displayMessage("Submitting form(s) to the server...");
                            }

                            @Override
                            protected void deliverResult(RecoveryActivity receiver, Integer result) {
                                if (result == ProcessAndSendTask.PROGRESS_LOGGED_OUT) {

                                    receiver.displayMessage("Log-in expired during send. Please press back and log in again");
                                    return;
                                }

                                int successfulSends = this.getSuccessfulSends();

                                if (result == FormUploadUtil.FULL_SUCCESS) {
                                    receiver.displayMessage("Send succesful. All  " + successfulSends + " forms were submitted");
                                } else if (result == FormUploadUtil.FAILURE) {
                                    String remainder = successfulSends > 0 ? " Only " + successfulSends + " were submitted" : "";
                                    receiver.displayMessage("There were errors submitting the forms." + remainder);
                                } else if (result == FormUploadUtil.TRANSPORT_FAILURE) {
                                    receiver.displayMessage("Unable to contact the remote server.");
                                }
                            }

                            @Override
                            protected void deliverUpdate(RecoveryActivity receiver, Long... update) {
                                //we don't need to deliver updates here, it happens on the notification bar
                            }

                            @Override
                            protected void deliverError(RecoveryActivity receiver, Exception e) {
                                Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, "Error in recovery form send: " + ForceCloseLogger.getStackTrace(e));
                                receiver.displayMessage("Error while sending : " + e.getMessage());
                            }

                        };

                mProcess.setListeners(CommCareApplication._().getSession().startDataSubmissionListener());

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
        if (!CommCareApplication._().isStorageAvailable()) {
            appState.setText("app state unavailable.");
            return;
        }

        if (CommCareApplication._().getCurrentApp().getAppResourceState() == CommCareApplication.STATE_CORRUPTED) {
            appState.setText("App install is corrupt. Make sure forms are sent before attempting recovery.");
            btnRecoverApp.setEnabled(true);
        } else {
            appState.setText("App is installed and valid");
            btnRecoverApp.setEnabled(false);
        }
    }

    private void updateSendFormsState() {
        sendForms.setEnabled(false);
        if (!CommCareApplication._().isStorageAvailable()) {
            txtUnsentForms.setText("unsent forms unavailable.");
            return;
        }

        try {
            CommCareApplication._().getSession();
        } catch (SessionUnavailableException sue) {
            txtUnsentForms.setText("Couldn't read unsent forms. Not Logged in");
            return;
        }

        SqlStorage<FormRecord> recordStorage = CommCareApplication._().getUserStorage(FormRecord.class);
        try {
            FormRecord[] records = StorageUtils.getUnsentRecords(recordStorage);
            if (records.length == 0) {
                txtUnsentForms.setText("This device has no unsent forms");
            } else {
                txtUnsentForms.setText("There are " + records.length + " unsent form(s) on this device");
                sendForms.setEnabled(true);
            }
        } catch (Exception e) {
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, e.getMessage());
            txtUnsentForms.setText("Couldn't read unsent forms. Error : " + e.getMessage());
        }
    }
}
