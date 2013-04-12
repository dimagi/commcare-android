/**
 * 
 */
package org.commcare.dalvik.activities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.DumpTask;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.tasks.ProcessAndSendTask.ProcessIssues;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.ReflectionUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.StorageFullException;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */

@ManagedUi(R.layout.screen_form_dump)
public class CommCareFormDumpActivity extends CommCareActivity<CommCareFormDumpActivity> {
	
	@UiElement(R.id.screen_bulk_image1)
	ImageView banner;
	
	@UiElement(value = R.id.screen_bulk_form_prompt, locale="bulk.form.prompt")
	TextView txtDisplayPrompt;
	
	@UiElement(value = R.id.screen_bulk_form_dump, locale="bulk.form.dump")
	Button btnDumpForms;
	
	@UiElement(value = R.id.screen_bulk_form_submit, locale="bulk.form.submit")
	Button btnSubmitForms;
	
	@UiElement(value = R.id.screen_bulk_form_messages, locale="bulk.form.messages")
	TextView txtInteractiveMessages;
	
	boolean done = false;
	
	AlertDialog mAlertDialog;
	static boolean acknowledgedRisk = false;
	
	static final int BULK_DUMP_ID = 2;
	static final int BULK_SEND_ID = 3;
	static final String KEY_NUMBER_DUMPED = "num_dumped";
	
	private int formsOnPhone;
	private int formsOnSD;

	/* (non-Javadoc)
	 * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		final String url = this.getString(R.string.PostURL);
		
		super.onCreate(savedInstanceState);
		
		//get number of unsynced forms for display purposes
    	Vector<Integer> ids = getUnsyncedForms();
    	File[] files = this.getDumpFiles();
    	
    	formsOnPhone = ids.size();
		formsOnSD = files.length;
		
		btnDumpForms.setText(Localization.get("bulk.form.dump.2", new String[] {""+formsOnPhone}));
		btnSubmitForms.setText(Localization.get("bulk.form.submit.2", new String[] {""+formsOnSD}));
		txtDisplayPrompt.setText(Localization.get("bulk.form.prompt", new String[] {""+formsOnPhone , ""+formsOnSD}));
		
		btnSubmitForms.setOnClickListener(new OnClickListener() {
			public void onClick(View v){
				
				//if there're no forms to dump, just return
				if(formsOnSD == 0){
					txtInteractiveMessages.setText(Localization.get("bulk.form.no.unsynced.submit"));
					TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					return;
				}
				
	    		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
				SendTask mSendTask = new SendTask(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), settings.getString("PostURL", url), txtInteractiveMessages){
					
					@Override
					protected void deliverResult( CommCareFormDumpActivity receiver, Boolean result) {
						
						if(result == Boolean.TRUE){
							
					        Intent i = new Intent(getIntent());
					        i.putExtra(KEY_NUMBER_DUMPED, formsOnSD);
							receiver.setResult(BULK_SEND_ID, i);
							receiver.finish();
							return;
						} else {
							//assume that we've already set the error message, but make it look scary
							receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
						}
					}

					@Override
					protected void deliverUpdate(CommCareFormDumpActivity receiver, String... update) {
						receiver.updateProgress(BULK_SEND_ID, update[0]);
						receiver.txtInteractiveMessages.setText(update[0]);
					}

					@Override
					protected void deliverError(CommCareFormDumpActivity receiver, Exception e) {
						receiver.txtInteractiveMessages.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
						receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					}
				};
				mSendTask.setTaskId(BULK_SEND_ID);
				mSendTask.connect(CommCareFormDumpActivity.this);
				mSendTask.execute();
			}
		});
		
		btnDumpForms.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				
				if(formsOnPhone == 0){
					txtInteractiveMessages.setText(Localization.get("bulk.form.no.unsynced.dump"));
					TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					return;
				}
				SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
				DumpTask mDumpTask = new DumpTask(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), settings.getString("PostURL", url), txtInteractiveMessages){

					@Override
					protected void deliverResult( CommCareFormDumpActivity receiver, Boolean result) {
						if(result == Boolean.TRUE){
					        Intent i = new Intent(getIntent());
					        i.putExtra(KEY_NUMBER_DUMPED, formsOnPhone);
							receiver.setResult(BULK_DUMP_ID, i);
							receiver.finish();
							return;
						} else {
							//assume that we've already set the error message, but make it look scary
							receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
						}
					}

					@Override
					protected void deliverUpdate(CommCareFormDumpActivity receiver, String... update) {
						receiver.updateProgress(BULK_DUMP_ID, update[0]);
						receiver.txtInteractiveMessages.setText(update[0]);
					}

					@Override
					protected void deliverError(CommCareFormDumpActivity receiver, Exception e) {
						receiver.txtInteractiveMessages.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
						receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					}
				};
				mDumpTask.setTaskId(BULK_DUMP_ID);
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
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	
    }
    
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
    
    public File[] getDumpFiles(){
    	ArrayList<String> externalMounts = FileUtil.getExternalMounts();
		String baseDir = externalMounts.get(0);
		String folderName = Localization.get("bulk.form.foldername");
		File dumpDirectory = new File( baseDir + "/" + folderName);
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
		onBackPressed();
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == BULK_DUMP_ID) {
			ProgressDialog progressDialog = new ProgressDialog(this);
			progressDialog.setTitle(Localization.get("bulk.dump.dialog.title"));
			progressDialog.setMessage(Localization.get("bulk.dump.dialog.progress", new String[] {"0"}));
			return progressDialog;
		}
		else if (id == BULK_SEND_ID) {
			ProgressDialog progressDialog = new ProgressDialog(this);
			progressDialog.setTitle(Localization.get("bulk.send.dialog.title"));
			progressDialog.setMessage(Localization.get("bulk.send.dialog.progress", new String[] {"0"}));
			return progressDialog;
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#taskCancelled(int)
	 */
	@Override
	public void taskCancelled(int id) {
		txtInteractiveMessages.setText(Localization.get("bulk.form.cancel"));
		this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
	}
	
	
}
