/**
 * 
 */
package org.commcare.dalvik.activities;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Vector;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.tasks.ProcessAndDumpTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.ReflectionUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
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

	/* (non-Javadoc)
	 * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		btnSubmitForms.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
    			//Go fetch us a file path!
			}
		});
		
		btnDumpForms.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				CommCareTask<String, String, Boolean, CommCareFormDumpActivity> task = new CommCareTask<String, String, Boolean, CommCareFormDumpActivity>() {

					@Override
					protected Boolean doTaskBackground(String... params) {
						
						// ensure that SD is available, writable, and not emulated
			
						boolean mExternalStorageAvailable = false;
						boolean mExternalStorageWriteable = false;
						
						boolean mExternalStorageEmulated = ReflectionUtil.fiddle();
						
						String state = Environment.getExternalStorageState();
						
						ArrayList<String> externalMounts = getExternalMounts();

						if (Environment.MEDIA_MOUNTED.equals(state)) {
						    // We can read and write the media
						    mExternalStorageAvailable = mExternalStorageWriteable = true;
						} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
						    // We can only read the media
						    mExternalStorageAvailable = true;
						    mExternalStorageWriteable = false;
						} else {
						    // Something else is wrong. It may be one of many other states, but all we need
						    //  to know is we can neither read nor write
						    mExternalStorageAvailable = mExternalStorageWriteable = false;
						}
						
						if(!mExternalStorageAvailable){
							publishProgress(("your external storage is not available"));
							return false;
						}
						if(!mExternalStorageWriteable){
							publishProgress(("your external storage is not writable"));
							return false;
						}
						if(mExternalStorageEmulated && externalMounts.size() == 0){
							publishProgress(("your external storage is emulated"));
							return false;
						}
						
						String baseDir = externalMounts.get(0);
						String folderName = Localization.get("bulk.form.foldername");
						
						System.out.println("405 making folder at: " + baseDir + "/"+folderName);
						
						File f = new File( baseDir + "/"+folderName);
						
						if(f.exists() && f.isDirectory()){
							f.delete();
						}
						
						File dumpDirectory = new File(baseDir + "/" + folderName);
						dumpDirectory.mkdirs();
						
				    	SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
				    	
				    	System.out.println("405: about to deal with storage");
				    	
				    	//Get all forms which are either unsent or unprocessed
				    	Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
				    	ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
				    	
				    	if(ids.size() > 0) {
				    		FormRecord[] records = new FormRecord[ids.size()];
				    		for(int i = 0 ; i < ids.size() ; ++i) {
				    			records[i] = storage.read(ids.elementAt(i).intValue());
				    		}
				    		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();

							ProcessAndDumpTask mProcessAndDumpTask = new ProcessAndDumpTask(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), dumpDirectory);
							mProcessAndDumpTask.execute(records);
							
				    		return true;
				    	} else {
				    		//Nothing.
				    		return false;
				    	}
					}

					@Override
					protected void deliverResult( CommCareFormDumpActivity receiver, Boolean result) {
						if(result == Boolean.TRUE){
							receiver.done = true;
							//receiver.evalState();
							receiver.setResult(Activity.RESULT_OK);
							receiver.finish();
							return;
						} else {
							//assume that we've already set the error message, but make it look scary
							receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
						}
					}

					@Override
					protected void deliverUpdate(CommCareFormDumpActivity receiver, String... update) {
						receiver.updateProgress(CommCareTask.GENERIC_TASK_ID, update[0]);
						receiver.txtInteractiveMessages.setText(update[0]);
					}

					@Override
					protected void deliverError(CommCareFormDumpActivity receiver, Exception e) {
						receiver.txtInteractiveMessages.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
						receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					}
				};
				
				task.connect(CommCareFormDumpActivity.this);
				task.execute();
			}
			
		});
		
		//mAlertDialog = popupWarningMessage();
		//	mAlertDialog.show();
	}
	
	/*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {

    }
    
    public static ArrayList<String> getExternalMounts() {
        final ArrayList<String> out = new ArrayList<String>();
        String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4).*rw.*";
        String s = "";
        try {
            final Process process = new ProcessBuilder().command("mount")
                    .redirectErrorStream(true).start();
            process.waitFor();
            final InputStream is = process.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                s = s + new String(buffer);
            }
            is.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        // parse output
        final String[] lines = s.split("\n");
        for (String line : lines) {
            if (!line.toLowerCase(Locale.US).contains("asec")) {
                if (line.matches(reg)) {
                    String[] parts = line.split(" ");
                    for (String part : parts) {
                        if (part.startsWith("/"))
                            if (!part.toLowerCase(Locale.US).contains("vold"))
                                out.add(part);
                    }
                }
            }
        }
        return out;
    }
    
    private AlertDialog popupWarningMessage(){
    	AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
    	alertDialogBuilder.setTitle("Title");
    	alertDialogBuilder
    		.setMessage("Do not use this unless you know otherwise")
    		.setCancelable(false)
    		.setPositiveButton("OK",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
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

	/* (non-Javadoc)
	 * @see org.commcare.android.framework.CommCareActivity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		evalState();
	}
	
	private void exitDump(){
		onBackPressed();
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		if(id == CommCareTask.GENERIC_TASK_ID) {
			ProgressDialog progressDialog = new ProgressDialog(this);
			progressDialog.setTitle(Localization.get("mult.install.title"));
			progressDialog.setMessage(Localization.get("mult.install.progress", new String[] {"0"}));
			return progressDialog;
		}
		return null;
	}

	private void evalState() {

	}
	
	/* (non-Javadoc)
	 * @see org.commcare.android.tasks.templates.CommCareTaskConnector#taskCancelled(int)
	 */
	@Override
	public void taskCancelled(int id) {
		txtInteractiveMessages.setText(Localization.get("mult.install.cancelled"));
		this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
	}
}
