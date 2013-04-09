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
import org.commcare.android.tasks.ProcessAndDumpTask;
import org.commcare.android.tasks.ProcessAndSendTask;
import org.commcare.android.tasks.ProcessAndDumpTask.ProcessIssues;
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
					
					private  Cipher getDecryptCipher(SecretKeySpec key) {
						Cipher cipher;
						try {
							cipher = Cipher.getInstance("AES");
							cipher.init(Cipher.DECRYPT_MODE, key);
							return cipher;
							//TODO: Something smart here;
						} catch (NoSuchAlgorithmException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (NoSuchPaddingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (InvalidKeyException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						return null;
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
				        	System.out.println("405 Added file: " + files[j].getName() +". Bytes to send: " + bytes);
				        }
				        
						//this.startSubmission(submissionNumber, bytes);
						
						final Cipher decrypter = getDecryptCipher(key);
						
						for(int j=0;j<files.length;j++){
							
							System.out.println("405: entering decrpy and copy loop");
							
							File f = files[j];
							if (f.getName().endsWith(".xml")) {
								try{
									FileUtil.copyFile(f, new File(myDir, f.getName()), decrypter, null);
								}
								catch(IOException ie){
									System.out.println("405 caught an ioexception: " + ie.getMessage());
								}
							}
							else{
								try{
									FileUtil.copyFile(f, new File(myDir, f.getName()));
								}
								catch(IOException ie){
									System.out.println("405 caught an ioexception: " + ie.getMessage());
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
							publishProgress(("bulk.form.sd.unavailable"));
							return false;
						}
						if(!mExternalStorageWriteable){
							publishProgress(("bulk.form.sd.unwritable"));
							return false;
						}
						if(mExternalStorageEmulated && externalMounts.size() == 0){
							publishProgress(("bulk.form.sd.emulated"));
							return false;
						}
						
						String baseDir = externalMounts.get(0);
						String folderName = Localization.get("bulk.form.foldername");
						
						File f = new File( baseDir + "/"+folderName);
						
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
				    		
							//ProcessAndDumpTask mProcessAndDumpTask = new ProcessAndDumpTask(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), dumpDirectory);
							//mProcessAndDumpTask.execute(records);
							
				    		
				    		try{
				    			
				    			results = new Long[records.length];
				    			for(int i = 0; i < records.length ; ++i ) {
				    				//Assume failure
				    				results[i] = ProcessAndSendTask.FAILURE;
				    			}
				    			
				    			publishProgress("starting");
				    			
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
			progressDialog.setTitle(Localization.get("bulk.form.dialog.title"));
			progressDialog.setMessage(Localization.get("bulk.form.dialog.progress", new String[] {"0"}));
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
