/**
 * 
 */
package org.commcare.dalvik.activities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.tasks.ResourceEngineTask;
import org.commcare.android.tasks.UnzipTask;
import org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author ctsims
 *
 */

@ManagedUi(R.layout.screen_multimedia_inflater)
public class InstallArchiveActivity extends CommCareActivity<InstallArchiveActivity> {

	private static final String LOG_TAG = "CommCare-ArchiveInstaller";

	private static final int REQUEST_FILE_LOCATION = 1;

	public static final String EXTRA_FILE_DESTINATION = "ccodk_mia_filedest";

	@UiElement(value = R.id.screen_multimedia_inflater_prompt, locale="archive.install.prompt")
	TextView txtDisplayPrompt;

	@UiElement(value = R.id.screen_multimedia_install_messages, locale="archive.install.state.empty")
	TextView txtInteractiveMessages;

	@UiElement(R.id.screen_multimedia_inflater_location)
	EditText editFileLocation;

	@UiElement(R.id.screen_multimedia_inflater_filefetch)
	ImageButton btnFetchFiles;

	@UiElement(value = R.id.screen_multimedia_inflater_install, locale="archive.install.button")
	Button btnInstallMultimedia;

	boolean done = false;

	public static String TAG = "install-archive";

	private String currentRef;

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
				try {
					startActivityForResult(intent, REQUEST_FILE_LOCATION);
				} catch(ActivityNotFoundException e) {
					Toast.makeText(InstallArchiveActivity.this, Localization.get("archive.install.no.browser"), Toast.LENGTH_LONG).show();
				}
			}
		});


		btnInstallMultimedia.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				currentRef = editFileLocation.getText().toString();

				UnzipTask<InstallArchiveActivity> mUnzipTask = new UnzipTask<InstallArchiveActivity>() {

					@Override
					protected void deliverResult( InstallArchiveActivity receiver, Integer result) {
						Log.d(TAG, "delivering unzip result");
						if(result > 0){
							receiver.onUnzipSuccessful(result);
							return;
						} else {
							//assume that we've already set the error message, but make it look scary
							receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
						}
					}

					@Override
					protected void deliverUpdate(InstallArchiveActivity receiver, String... update) {
						Log.d(TAG, "delivering unzip upate");
						receiver.updateProgress(CommCareTask.GENERIC_TASK_ID, update[0]);
						receiver.txtInteractiveMessages.setText(update[0]);
					}

					@Override
					protected void deliverError(InstallArchiveActivity receiver, Exception e) {
						Log.d(TAG, "unzip deliver error: " + e.getMessage());
						receiver.txtInteractiveMessages.setText(Localization.get("archive.install.error", new String[] {e.getMessage()}));
						receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					}
				};

				String writeDirectory = currentRef;
				File mFile = new File(currentRef);
				File parent = mFile.getParentFile();
				//String fn = parent.toString() + "/" + ARCHIVE_UNZIP_LOCATION;
				String fn = getFolderName();
				FileUtil.deleteFileOrDir(fn);

				mUnzipTask.connect(InstallArchiveActivity.this);
				Log.d(TAG, "executing task with: " + fn + " , " + writeDirectory);
				mUnzipTask.execute(writeDirectory, fn);

			}

		});
	}

	protected void onUnzipSuccessful(Integer result) {

		ApplicationRecord newRecord = new ApplicationRecord(PropertyUtils.genUUID().replace("-",""), ApplicationRecord.STATUS_UNINITIALIZED);
		CommCareApp app = new CommCareApp(newRecord);

		int lastIndex = currentRef.lastIndexOf("/");
		int dotIndex = currentRef.lastIndexOf(".");

		String fileNameString = currentRef.substring(lastIndex, dotIndex);

		String myRef = getFolderName();

		File mFile = new File(myRef+"/"+fileNameString);

		try {
			FileUtil.copyFileDeep(mFile, new File(myRef));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		String ref = "jr://archive/profile.xml";

		Intent i = new Intent(getIntent());
        i.putExtra("archive-ref", ref);
        setResult(RESULT_OK, i);
        finish();

	}

	public void updateProgress(int done, int total, int phase) {
		updateProgress(CommCareTask.GENERIC_TASK_ID, Localization.get("profile.found", new String[]{""+done,""+total}));
	}

	public void reportSuccess(boolean appChanged) {
		//If things worked, go ahead and clear out any warnings to the contrary
		CommCareApplication._().clearNotifications("install_update");

		if(!appChanged) {
			Toast.makeText(this, Localization.get("updates.success"), Toast.LENGTH_LONG).show();
		}
		done(appChanged);
	}

	public void done(boolean requireRefresh) {
		//TODO: We might have gotten here due to being called from the outside, in which
		//case we should manually start up the home activity
		Intent i = new Intent(getApplicationContext(), CommCareHomeActivity.class);
		i.putExtra(CommCareSetupActivity.KEY_REQUIRE_REFRESH, requireRefresh);
		startActivity(i);
		finish();
		return;
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
			progressDialog.setTitle("Installing Resources");
			progressDialog.setMessage("Installing resource 0...");
			return progressDialog;
		}
		else if(id == UnzipTask.UNZIP_TASK_ID) {
			ProgressDialog progressDialog = new ProgressDialog(this);
			progressDialog.setTitle("Unzipping Files");
			progressDialog.setMessage("Unzipping...");
			return progressDialog;
		}
		return null;
	}

	private void evalState() {
		if(done) {
			txtInteractiveMessages.setText(Localization.get("archive.install.state.done"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
			btnInstallMultimedia.setEnabled(false);
			return;
		}

		String location = editFileLocation.getText().toString();
		if("".equals(location)) {
			txtInteractiveMessages.setText(Localization.get("archive.install.state.empty"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
			btnInstallMultimedia.setEnabled(false);
			return;
		}

		if(!(new File(location)).exists()) {
			txtInteractiveMessages.setText(Localization.get("archive.install.state.invalid.path"));
			this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
			btnInstallMultimedia.setEnabled(false);
			return;
		}

		else {
			txtInteractiveMessages.setText(Localization.get("archive.install.state.ready"));
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
		txtInteractiveMessages.setText(Localization.get("archive.install.cancelled"));
		this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
	}

	public static String getFolderName(){
		return CommCareApplication._().getAndroidFsRoot() + "app/" +  CommCareApplication._().getArchiveUUID();
	}

}
