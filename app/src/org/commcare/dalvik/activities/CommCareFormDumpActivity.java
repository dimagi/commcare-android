/**
 * 
 */
package org.commcare.dalvik.activities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Vector;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.tasks.DataSubmissionListener;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.tasks.ProcessAndSendTask.ProcessIssues;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.FileUtil;
import org.commcare.android.util.ReflectionUtil;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.storage.StorageFullException;

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
		
    	Vector<Integer> ids = getUnsyncedForms();
		
    	File[] files = this.getDumpFiles();
    	
    	formsOnPhone = ids.size();
    	
		formsOnSD = files.length;
		
		btnDumpForms.setText(Localization.get("bulk.form.dump.2", new String[] {""+formsOnPhone}));
		btnSubmitForms.setText(Localization.get("bulk.form.submit.2", new String[] {""+formsOnSD}));
		
		txtDisplayPrompt.setText(Localization.get("bulk.form.prompt", new String[] {""+formsOnPhone , ""+formsOnSD}));
		
		btnSubmitForms.setOnClickListener(new OnClickListener() {
			public void onClick(View v){
	    		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
				SendTask mSendTask = new SendTask(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), settings.getString("PostURL", url), txtInteractiveMessages){
					
					@Override
					protected void deliverResult( CommCareFormDumpActivity receiver, Boolean result) {
						
						if(result == Boolean.TRUE){
							
					        Intent i = new Intent(getIntent());
					        i.putExtra(KEY_NUMBER_DUMPED, formsOnSD);
							
							
							receiver.done = true;
							//receiver.evalState();
							receiver.setResult(BULK_SEND_ID);
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
				if(formsOnSD == 0){
					txtInteractiveMessages.setText(Localization.get("bulk.form.no.unsynced.submit"));
					TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
				}
				else{
					mSendTask.connect(CommCareFormDumpActivity.this);
					mSendTask.execute();
				}
			}
		});
		
		btnDumpForms.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				CommCareTask<String, String, Boolean, CommCareFormDumpActivity> task = new CommCareTask<String, String, Boolean, CommCareFormDumpActivity>(){
					
					File dumpFolder;
					Long[] results;
					private String[] SUPPORTED_FILE_EXTS = {".xml", ".jpg", ".3gpp", ".3gp"};

					private String getExceptionText (Exception e) {
						try {
							ByteArrayOutputStream bos = new ByteArrayOutputStream();
							e.printStackTrace(new PrintStream(bos));
							return new String(bos.toByteArray());
						} catch(Exception ex) {
							return null;
						}
					}
					
					private long sendInstance(int submissionNumber, File folder, SecretKeySpec key) throws FileNotFoundException {
						
				        File[] files = folder.listFiles();
				        
				        File myDir = new File(dumpFolder, folder.getName());
				        myDir.mkdirs();
				        
				        if(files == null) {
				        	//make sure external storage is available to begin with.
				        	String state = Environment.getExternalStorageState();
				        	if (!Environment.MEDIA_MOUNTED.equals(state)) {
				        		//If so, just bail as if the user had logged out.
				        		throw new SessionUnavailableException("External Storage Removed");
				        	} else {
				        		throw new FileNotFoundException("No directory found at: " + folder.getAbsoluteFile());
				        	}
				        } 

				        //If we're listening, figure out how much (roughly) we have to send
						long bytes = 0;
				        for (int j = 0; j < files.length; j++) {
				        	//Make sure we'll be sending it
				        	boolean supported = false;
				        	for(String ext : SUPPORTED_FILE_EXTS) {
				        		if(files[j].getName().endsWith(ext)) {
				        			supported = true;
				        			break;
				        		}
				        	}
				        	if(!supported) { continue;}
				        	
				        	bytes += files[j].length();
				        }
				        
						//this.startSubmission(submissionNumber, bytes);
						
						final Cipher decrypter = ProcessAndSendTask.getDecryptCipher(key);
						
						for(int j=0;j<files.length;j++){
							
							File f = files[j];
							// This is not the ideal long term solution for determining whether we need decryption, but works
							if (f.getName().endsWith(".xml")) {
								try{
									FileUtil.copyFile(f, new File(myDir, f.getName()), decrypter, null);
								}
								catch(IOException ie){
									publishProgress(("File writing failed: " + ie.getMessage()));
									return ProcessAndSendTask.FAILURE;
								}
							}
							else{
								try{
									FileUtil.copyFile(f, new File(myDir, f.getName()));
								}
								catch(IOException ie){
									publishProgress(("File writing failed: " + ie.getMessage()));
									return ProcessAndSendTask.FAILURE;
								}
							}
						}
				        return ProcessAndSendTask.FULL_SUCCESS;
					}
					
					@Override
					protected Boolean doTaskBackground(String... params) {
						
						// ensure that SD is available, writable, and not emulated
			
						boolean mExternalStorageAvailable = false;
						boolean mExternalStorageWriteable = false;
						
						boolean mExternalStorageEmulated = ReflectionUtil.fiddle();
						
						String state = Environment.getExternalStorageState();
						
						ArrayList<String> externalMounts = FileUtil.getExternalMounts();

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
							publishProgress(Localization.get("bulk.form.sd.unavailable"));
							return false;
						}
						if(!mExternalStorageWriteable){
							publishProgress(Localization.get("bulk.form.sd.unwritable"));
							return false;
						}
						if(mExternalStorageEmulated && externalMounts.size() == 0){
							publishProgress(Localization.get("bulk.form.sd.emulated"));
							return false;
						}
						
						String baseDir = externalMounts.get(0);
						String folderName = Localization.get("bulk.form.foldername");
						
						File f = new File( baseDir + "/" + folderName);
						
						if(f.exists() && f.isDirectory()){
							f.delete();
						}
						
						File dumpDirectory = new File(baseDir + "/" + folderName);
						dumpDirectory.mkdirs();
						
				    	SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
				    	
				    	//Get all forms which are either unsent or unprocessed
				    	Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
				    	ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
				    	
				    	if(ids.size() > 0) {
				    		FormRecord[] records = new FormRecord[ids.size()];
				    		for(int i = 0 ; i < ids.size() ; ++i) {
				    			records[i] = storage.read(ids.elementAt(i).intValue());
				    		}

				    		dumpFolder = dumpDirectory;
		
				    		try{
				    			
				    			results = new Long[records.length];
				    			for(int i = 0; i < records.length ; ++i ) {
				    				//Assume failure
				    				results[i] = ProcessAndSendTask.FAILURE;
				    			}
				    			
				    			publishProgress(Localization.get("bulk.form.start"));
				    			
				    			for(int i = 0 ; i < records.length ; ++i) {
				    				FormRecord record = records[i];
				    				try{
				    					//If it's unsent, go ahead and send it
				    					if(FormRecord.STATUS_UNSENT.equals(record.getStatus())) {
				    						File folder;
				    						try {
				    							folder = new File(record.getPath(getApplicationContext())).getCanonicalFile().getParentFile();
				    						} catch (IOException e) {
				    							Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Bizarre. Exception just getting the file reference. Not removing." + getExceptionText(e));
				    							continue;
				    						}
				    						
				    						//Good!
				    						//Time to Send!
				    						try {
				    							results[i] = sendInstance(i, folder, new SecretKeySpec(record.getAesKey(), "AES"));
				    							
				    						} catch (FileNotFoundException e) {
				    							if(CommCareApplication._().isStorageAvailable()) {
				    								//If storage is available generally, this is a bug in the app design
				    								Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Removing form record because file was missing|" + getExceptionText(e));
				    							} else {
				    								//Otherwise, the SD card just got removed, and we need to bail anyway.
				    								CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.StorageRemoved), true);
				    								break;
				    							}
				    							continue;
				    						}
				    					
				    						//Check for success
				    						if(results[i].intValue() == ProcessAndSendTask.FULL_SUCCESS) {
				    						    new FormRecordCleanupTask(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform()).wipeRecord(record);
				    						    publishProgress(Localization.get("bulk.form.dialog.progress",new String[]{""+i, ""+results[i].intValue()}));
				    				        }
				    					}
				    					
				    					
				    				} catch (StorageFullException e) {
				    					Logger.log(AndroidLogger.TYPE_ERROR_WORKFLOW, "Really? Storage full?" + getExceptionText(e));
				    					throw new RuntimeException(e);
				    				} catch(SessionUnavailableException sue) {
				    					throw sue;
				    				} catch (Exception e) {
				    					//Just try to skip for now. Hopefully this doesn't wreck the model :/
				    					Logger.log(AndroidLogger.TYPE_ERROR_DESIGN, "Totally Unexpected Error during form submission" + getExceptionText(e));
				    					continue;
				    				}  
				    			}
				    			
				    			long result = 0;
				    			for(int i = 0 ; i < records.length ; ++ i) {
				    				if(results[i] > result) {
				    					result = results[i];
				    				}
				    			}
				    			
				    			//this.endSubmissionProcess();
				    			
				    			} 
				    			catch(SessionUnavailableException sue) {
				    				this.cancel(false);
				    				return false;
				    			}
				    		
				    		//
				    		//
				    		return true;
				    	} else {
				    		publishProgress(Localization.get("bulk.form.no.unsynced"));
				    		return false;
				    	}
					}

					@Override
					protected void deliverResult( CommCareFormDumpActivity receiver, Boolean result) {
						if(result == Boolean.TRUE){
							
					        Intent i = new Intent(getIntent());
					        i.putExtra(KEY_NUMBER_DUMPED, formsOnPhone);
							
							receiver.done = true;
							//receiver.evalState();
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
				task.setTaskId(BULK_DUMP_ID);
				if(formsOnPhone == 0){
					txtInteractiveMessages.setText(Localization.get("bulk.form.no.unsynced.dump"));
					TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
				}
				else{
					task.connect(CommCareFormDumpActivity.this);
					task.execute();
				}
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
