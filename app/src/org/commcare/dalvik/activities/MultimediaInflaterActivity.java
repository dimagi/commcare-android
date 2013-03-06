/**
 * 
 */
package org.commcare.dalvik.activities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.javarosa.core.services.locale.Localization;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * @author ctsims
 *
 */

@ManagedUi(R.layout.screen_multimedia_inflater)
public class MultimediaInflaterActivity extends CommCareActivity<MultimediaInflaterActivity> {
	
	private static final int REQUEST_FILE_LOCATION = 1;
	
	public static final String EXTRA_FILE_DESTINATION = "ccodk_mia_filedest";
	
	@UiElement(value = R.id.screen_multimedia_inflater_prompt, locale="mult.install.prompt")
	TextView txtDisplayPrompt;
	
	@UiElement(value = R.id.screen_multimedia_install_messages, locale="mult.install.state.empty")
	TextView txtInteractiveMessages;
	
	@UiElement(R.id.screen_multimedia_inflater_location)
	EditText editFileLocation;
	
	@UiElement(R.id.screen_multimedia_inflater_filefetch)
	ImageButton btnFetchFiles;
	
	@UiElement(value = R.id.screen_multimedia_inflater_install, locale="mult.install.button")
	Button btnInstallMultimedia;
	
	boolean done = false;

	/* (non-Javadoc)
	 * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		btnFetchFiles.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
    			//Go fetch us a file path!
    		    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
    		    intent.setType("file/*");
    		    startActivityForResult(intent, REQUEST_FILE_LOCATION);	
			}
		});
		
		final String destination = this.getIntent().getStringExtra(EXTRA_FILE_DESTINATION);
		
		
		btnInstallMultimedia.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				CommCareTask<String, String, Boolean, MultimediaInflaterActivity> task = new CommCareTask<String, String, Boolean, MultimediaInflaterActivity>() {

					@Override
					protected Boolean doTaskBackground(String... params) {
						File archive = new File(params[0]);
						File destination = new File(params[1]);
						
						int count = 0;
						ZipFile zipfile;
						//From stackexchange
						try {
							zipfile = new ZipFile(archive);
						} catch(IOException ioe) {
							publishProgress(Localization.get("mult.install.bad"));
							return false;
						}
		                for (Enumeration e = zipfile.entries(); e.hasMoreElements();) {
		                	Localization.get("mult.install.progress", new String[] {String.valueOf(count)});
		                	count++;
		                    ZipEntry entry = (ZipEntry) e.nextElement();
		                    
		                    if (entry.isDirectory()) {
		                    	FileUtil.createFolder(new File(destination, entry.getName()).toString());
		                    }

		                    File outputFile = new File(destination, entry.getName());
		                    if (!outputFile.getParentFile().exists()) {
		                    	FileUtil.createFolder(outputFile.getParentFile().toString());
		                    }
		                    if(outputFile.exists()) {
		                    	//Try to overwrite if we can
		                    	if(!outputFile.delete()) {
		                    		//If we couldn't, just skip for now
		                    		continue;
		                    	}
		                    }
		                    BufferedInputStream inputStream;
		                    try {
		                    	inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
		                    } catch(IOException ioe) {
	                    		this.publishProgress(Localization.get("mult.install.progress.badentry", new String[] {entry.getName()}));
	                    		return false;
		                    }
		                    
		                    BufferedOutputStream outputStream;
		                    try {
		                    	outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
		                    } catch(IOException ioe) {
	                    		this.publishProgress(Localization.get("mult.install.progress.baddest", new String[] {outputFile.getName()}));
	                    		return false;
	                    	}

		                    try {
		                    	try {
		                    		AndroidStreamUtil.writeFromInputToOutput(inputStream, outputStream);
		                    	} catch(IOException ioe) {
		                    		this.publishProgress(Localization.get("mult.install.progress.errormoving"));
		                    		return false;
		                    	}
		                    } finally {
		                    	try {
		                        outputStream.close();
		                    	} catch(IOException ioe) {}
		                    	try {
		                        inputStream.close();
		                    	} catch(IOException ioe) {}
		                    }
		                }

						
						return true;
					}

					@Override
					protected void deliverResult( MultimediaInflaterActivity receiver, Boolean result) {
						if(result == Boolean.TRUE){
							receiver.done = true;
							receiver.evalState();
							receiver.setResult(Activity.RESULT_OK);
							receiver.finish();
							return;
						} else {
							//assume that we've already set the error message, but make it look scary
							receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
						}
					}

					@Override
					protected void deliverUpdate(MultimediaInflaterActivity receiver, String... update) {
						receiver.updateProgress(CommCareTask.GENERIC_TASK_ID, update[0]);
						receiver.txtInteractiveMessages.setText(update[0]);
					}

					@Override
					protected void deliverError(MultimediaInflaterActivity receiver, Exception e) {
						receiver.txtInteractiveMessages.setText(Localization.get("mult.install.error", new String[] {e.getMessage()}));
						receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					}
				};
				
				task.connect(MultimediaInflaterActivity.this);
				task.execute(editFileLocation.getText().toString(), destination);
			}
			
		});
	}
	
	/*
     * (non-Javadoc)
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	if(requestCode == REQUEST_FILE_LOCATION) {
    		if(resultCode == Activity.RESULT_OK) {
    		    String filePath = intent.getData().getPath();
    		    editFileLocation.setText(filePath);
    		}
    	}
    }

	/* (non-Javadoc)
	 * @see org.commcare.android.framework.CommCareActivity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();
		evalState();
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
		if(done) {
			txtInteractiveMessages.setText(Localization.get("mult.install.state.done"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
			btnInstallMultimedia.setEnabled(false);
			return;
		}
		
		String location = editFileLocation.getText().toString();
		if("".equals(location)) {
			txtInteractiveMessages.setText(Localization.get("mult.install.state.empty"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
			btnInstallMultimedia.setEnabled(false);
			return;
		}
		
		if(!(new File(location)).exists()) {
			txtInteractiveMessages.setText(Localization.get("mult.install.state.invalid.path"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
			btnInstallMultimedia.setEnabled(false);
			return;
		}
		
		else {
			txtInteractiveMessages.setText(Localization.get("mult.install.state.ready"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
			btnInstallMultimedia.setEnabled(true);
			return;
		}
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
