package org.commcare.dalvik.activities;

import java.io.File;
import java.util.Vector;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.DumpTask;
import org.commcare.android.tasks.ExceptionReportTask;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.MarkupUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author wspride
 *
 */

@ManagedUi(R.layout.screen_form_dump)
public class CommCareFormDumpActivity extends CommCareActivity<CommCareFormDumpActivity> {
     
    @UiElement(value = R.id.screen_bulk_form_prompt, locale="bulk.form.prompt")
    TextView txtDisplayPrompt;
    
    @UiElement(value = R.id.screen_bulk_form_dump, locale="bulk.form.dump")
    Button btnDumpForms;
    
    @UiElement(value = R.id.screen_bulk_form_submit, locale="bulk.form.submit")
    Button btnSubmitForms;
    
    @UiElement(value = R.id.screen_bulk_form_messages, locale="bulk.form.messages")
    TextView txtInteractiveMessages;
    
    public static final String AIRPLANE_MODE_CATEGORY = "airplane-mode";
    
    boolean done = false;
    
    AlertDialog mAlertDialog;
    static boolean acknowledgedRisk = false;
    
    static final String KEY_NUMBER_DUMPED = "num_dumped";
    
    public static final String EXTRA_FILE_DESTINATION = "ccodk_mia_filedest";
    
    private int formsOnPhone;
    private int formsOnSD;
    
    protected String filepath;

    /* (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
        final String url = this.getString(R.string.PostURL);
        
        super.onCreate(savedInstanceState);
        
        updateCounters();
        
        btnSubmitForms.setOnClickListener(new OnClickListener() {
            public void onClick(View v){
                
                formsOnSD = getDumpFiles().length;
                
                //if there're no forms to dump, just return
                if(formsOnSD == 0){
                    txtInteractiveMessages.setText(localize("bulk.form.no.unsynced.submit"));
                    TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                    return;
                }
                
                SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
                SendTask<CommCareFormDumpActivity> mSendTask = new SendTask<CommCareFormDumpActivity>(getApplicationContext(), settings.getString("PostURL", url), getFolderPath()){
                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverResult(java.lang.Object, java.lang.Object)
                     */
                    @Override
                    protected void deliverResult( CommCareFormDumpActivity receiver, Boolean result) {
                        
                        if(result == Boolean.TRUE){
                            CommCareApplication._().clearNotifications(AIRPLANE_MODE_CATEGORY);
                            Intent i = new Intent(getIntent());
                            i.putExtra(KEY_NUMBER_DUMPED, formsOnSD);
                            receiver.setResult(BULK_SEND_ID, i);
                            receiver.finish();
                            return;
                        } else {
                            //assume that we've already set the error message, but make it look scary
                            CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_AirplaneMode, AIRPLANE_MODE_CATEGORY));
                            receiver.updateCounters();
                            receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                        }
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverUpdate(java.lang.Object, java.lang.Object[])
                     */
                    @Override
                    protected void deliverUpdate(CommCareFormDumpActivity receiver, String... update) {
                        receiver.updateProgress(update[0], BULK_SEND_ID);
                        receiver.txtInteractiveMessages.setText(update[0]);
                    }
                    
                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverError(java.lang.Object, java.lang.Exception)
                     */
                    @Override
                    protected void deliverError(CommCareFormDumpActivity receiver, Exception e) {
                        Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "SendTask error: " + ExceptionReportTask.getStackTrace(e));
                        receiver.txtInteractiveMessages.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
                        receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                    }
                };
                mSendTask.connect(CommCareFormDumpActivity.this);
                mSendTask.execute();
            }
        });
        
        btnDumpForms.setOnClickListener(new OnClickListener() {
            /*
             * (non-Javadoc)
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {
                
                if(formsOnPhone == 0){
                    txtInteractiveMessages.setText(Localization.get("bulk.form.no.unsynced.dump"));
                    TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                    return;
                }
                SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
                DumpTask mDumpTask = new DumpTask(getApplicationContext(), txtInteractiveMessages){
                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverResult(java.lang.Object, java.lang.Object)
                     */
                    @Override
                    protected void deliverResult( CommCareFormDumpActivity receiver, Boolean result) {
                        if(result == Boolean.TRUE){
                            Intent i = new Intent(getIntent());
                            i.putExtra(KEY_NUMBER_DUMPED, formsOnPhone);
                            receiver.setResult(BULK_DUMP_ID, i);
                            receiver.finish();
                        } else {
                            //assume that we've already set the error message, but make it look scary
                            receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                        }
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverUpdate(java.lang.Object, java.lang.Object[])
                     */
                    @Override
                    protected void deliverUpdate(CommCareFormDumpActivity receiver, String... update) {
                        receiver.updateProgress(update[0], BULK_DUMP_ID);
                        receiver.txtInteractiveMessages.setText(update[0]);
                    }

                    /*
                     * (non-Javadoc)
                     * @see org.commcare.android.tasks.templates.CommCareTask#deliverError(java.lang.Object, java.lang.Exception)
                     */
                    @Override
                    protected void deliverError(CommCareFormDumpActivity receiver, Exception e) {
                        receiver.txtInteractiveMessages.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
                        receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                    }
                };
                mDumpTask.connect(CommCareFormDumpActivity.this);
                mDumpTask.execute();
            }
        });
        
        mAlertDialog = popupWarningMessage();
        
        if(!acknowledgedRisk){
            mAlertDialog.show();
        }
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    
    private AlertDialog popupWarningMessage(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(Localization.get("bulk.form.alert.title"));
        alertDialogBuilder
            .setMessage(Localization.get("bulk.form.warning"))
            .setCancelable(false)
            .setPositiveButton("OK",new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    acknowledgedRisk = true;
                    dialog.dismiss();
                    dialog.cancel();
                }
              })
            .setNegativeButton("No",new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog,int id) {
                    dialog.dismiss();
                    exitDump();
                }
            });
            AlertDialog alertDialog = alertDialogBuilder.create();
            return alertDialog;
            
    }
    
    public void updateCounters(){
        Vector<Integer> ids = getUnsyncedForms();
        File[] files = getDumpFiles();
        
        formsOnPhone = ids.size();
        formsOnSD = files.length;
        
        setDisplayText();
    }
    
    public void setDisplayText(){
        btnDumpForms.setText(this.localize("bulk.form.dump.2", new String[] {""+formsOnPhone}));
        btnSubmitForms.setText(this.localize("bulk.form.submit.2", new String[] {""+formsOnSD}));
        txtDisplayPrompt.setText(this.localize("bulk.form.prompt", new String[] {""+formsOnPhone , ""+formsOnSD}));
    }
    
    public String getFolderName(){
        SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
        String folderName = settings.getString(CommCarePreferences.DUMP_FOLDER_PATH    , Localization.get("bulk.form.foldername"));
        return folderName;
    }
    
    public File getFolderPath() {
        
        String fileRoot = FileUtil.getDumpDirectory(this);
        
        if (fileRoot == null){
            return null;
        }
        
        String folderName = getFolderName();
        
        File dumpDirectory = new File( fileRoot + "/" + folderName);
        
        return dumpDirectory;
    }
    
    public File[] getDumpFiles(){

        File dumpDirectory = getFolderPath();
        
        if(dumpDirectory == null || !dumpDirectory.isDirectory()){
            return new File[] {};
        }
            
        File[] files = dumpDirectory.listFiles();
            
        return files;
    }
    
    public Vector<Integer> getUnsyncedForms(){
        SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
        //Get all forms which are either unsent or unprocessed
        Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
        ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
        return ids;
    }

    /* (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onResume()
     */
    @Override
    protected void onResume() {
        super.onResume();
    }
    
    private void exitDump(){
        finish();
    }
    
    /* (non-Javadoc)
     * @see org.commcare.android.tasks.templates.CommCareTaskConnector#taskCancelled(int)
     */
    @Override
    public void taskCancelled(int id) {
        txtInteractiveMessages.setText(Localization.get("bulk.form.cancel"));
        this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
    }
    
    /** Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#generateProgressDialog(int)
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        String title, message;
        if(taskId == DumpTask.BULK_DUMP_ID) {
            title = Localization.get("bulk.dump.dialog.title");
            message = Localization.get("bulk.dump.dialog.progress", new String[] {"0"});
        }
        else if (taskId == SendTask.BULK_SEND_ID) {
            title = Localization.get("bulk.send.dialog.title");
            message = Localization.get("bulk.send.dialog.progress", new String[] {"0"});
        }
        else {
            System.out.println("WARNING: taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in CommCareFormDumpActivity");
            return null;
        }
        return CustomProgressDialog.newInstance(title, message, taskId);
    }
    
    
}
