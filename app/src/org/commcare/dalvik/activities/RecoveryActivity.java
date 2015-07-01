package org.commcare.dalvik.activities;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.util.FormUploadUtil;
import org.commcare.android.util.MarkupUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.StorageUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.services.CommCareSessionService;
import org.javarosa.core.services.Logger;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */
@ManagedUi(R.layout.screen_recovery)
public class RecoveryActivity extends CommCareActivity<RecoveryActivity> {
    
    private static final int SEND_TASK_ID = 100;
    private static final int RECOVER_TASK_ID = 101;

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
    
    
    /* (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if(this.getDestroyedActivityState() != null) {
            //We just rotated or whatever, don't re-initialize everything
        } else {
            //Fresh Start, statewise.
            updateSendFormsState();
            updateRecoverAppState();
        }
        
        sendForms.setOnClickListener(new OnClickListener() {

            /*
             * (non-Javadoc)
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @SuppressLint("NewApi")
            @Override
            public void onClick(View v) {
                FormRecord[] records = StorageUtils.getUnsentRecords(CommCareApplication._().getUserStorage(FormRecord.class));
                SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
                
                ProcessAndSendTask<RecoveryActivity> mProcess = new ProcessAndSendTask<RecoveryActivity>(RecoveryActivity.this, settings.getString("PostURL", 
                        RecoveryActivity.this.getString(R.string.PostURL)), SEND_TASK_ID, true){

                    /* (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#onPreExecute()
                     */
                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        displayMessage("Submitting form(s) to the server...");
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverResult(java.lang.Object, java.lang.Object)
                     */
                    @Override
                    protected void deliverResult(RecoveryActivity receiver, Integer result) {
                         if(result == ProcessAndSendTask.PROGRESS_LOGGED_OUT) {
                            receiver.displayMessage("Log-in expired during send. Please press back and log in again");
                            return;
                        }
                         
                        int successfulSends = this.getSuccesfulSends();
                        
                        if(result == FormUploadUtil.FULL_SUCCESS) {
                            receiver.displayMessage("Send succesful. All  " + successfulSends + " forms were submitted");
                        } else if(result == FormUploadUtil.FAILURE) {
                            String remainder = successfulSends > 0 ? " Only " + successfulSends + " were submitted" : "";
                            receiver.displayMessage("There were errors submitting the forms." + remainder);
                        } else if(result == FormUploadUtil.TRANSPORT_FAILURE){
                            receiver.displayMessage("Unable to contact the remote server.");
                        } else {
                            
                        } 

                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverUpdate(java.lang.Object, java.lang.Object[])
                     */
                    @Override
                    protected void deliverUpdate(RecoveryActivity receiver, Long... update) {
                        //we don't need to deliver updates here, it happens on the notification bar
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverError(java.lang.Object, java.lang.Exception)
                     */
                    @Override
                    protected void deliverError(RecoveryActivity receiver,Exception e) {
                        Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION,"Error in recovery form send: " + ExceptionReportTask.getStackTrace(e));
                        receiver.displayMessage("Error while sending : " + e.getMessage());
                    }
                    
                };

                try {
                    mProcess.setListeners(CommCareApplication._().getSession().startDataSubmissionListener());
                } catch (SessionUnavailableException sue) {
                    // abort since it looks like the session expired
                    displayMessage("CommCare session is no longer available.");
                    return;
                }

                mProcess.connect(RecoveryActivity.this);
                
                //Execute on a true multithreaded chain. We should probably replace all of our calls with this
                //but this is the big one for now.
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
                    mProcess.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, records);
                } else {
                    mProcess.execute(records);
                }
                
                
                
            }
        });
        
        btnRecoverApp.setOnClickListener(new OnClickListener() {

            /*
             * (non-Javadoc)
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {
                displayMessage("App recovery is not yet enabled. Please clear user data (After sending all of your forms!) and re-install.");
            }
        });
    }

    protected void displayMessage(String text) {
        txtUserMessage.setText(this.localize(text));
    }

    /* (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#startBlockingForTask(int)
     */
    @Override
    public void startBlockingForTask(int id) {
        btnRecoverApp.setEnabled(false);
        sendForms.setEnabled(false);
    }

    /* (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#stopBlockingForTask(int)
     */
    @Override
    public void stopBlockingForTask(int id) {
        updateSendFormsState();
        updateRecoverAppState();
    }

    private void updateRecoverAppState() {
        btnRecoverApp.setEnabled(false);
        if(!CommCareApplication._().isStorageAvailable()) {
            appState.setText("app state unavailable.");
            return;
        }
        
        
        if(CommCareApplication._().getAppResourceState() == CommCareApplication.STATE_CORRUPTED) {
            appState.setText("App install is corrupt. Make sure forms are sent before attempting recovery.");
            btnRecoverApp.setEnabled(true);
            return;
        } else {
            appState.setText("App is installed and valid");
            btnRecoverApp.setEnabled(false);
            return;
        }
    }

    private void updateSendFormsState() {
        sendForms.setEnabled(false);
        if(!CommCareApplication._().isStorageAvailable()) {
            txtUnsentForms.setText("unsent forms unavailable.");
            return;
        }
        
        
        try {
            CommCareSessionService session = CommCareApplication._().getSession();
        } catch(SessionUnavailableException sue) {
            txtUnsentForms.setText("Couldn't read unsent forms. Not Logged in");
            return;
        }
            
        try {
            FormRecord[] records = StorageUtils.getUnsentRecords(CommCareApplication._().getUserStorage(FormRecord.class));
            if(records.length == 0) {
                txtUnsentForms.setText("This device has no unsent forms");
            } else{
                txtUnsentForms.setText("There are " + records.length + " unsent form(s) on this device");
                sendForms.setEnabled(true);
            }
        } catch(Exception e) {
            Logger.log(AndroidLogger.TYPE_ERROR_ASSERTION, e.getMessage());
            txtUnsentForms.setText("Couldn't read unsent forms. Error : " + e.getMessage());
        }

    }

    /* (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onResume()
     */
    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
    }
    
}
