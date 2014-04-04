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

	public static String TAG = "install-archive";

	public static String ARCHIVE_UNZIP_LOCATION = "install-archives";

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
					Toast.makeText(InstallArchiveActivity.this, Localization.get("mult.install.no.browser"), Toast.LENGTH_LONG).show();
				}
			}
		});


		btnInstallMultimedia.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				String ref = editFileLocation.getText().toString();

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
						receiver.txtInteractiveMessages.setText(Localization.get("mult.install.error", new String[] {e.getMessage()}));
						receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
					}
				};

				String writeDirectory = ref;
				File mFile = new File(ref);
				File parent = mFile.getParentFile();
				String fn = parent.toString() + "/" + ARCHIVE_UNZIP_LOCATION;
				FileUtil.deleteFileOrDir(fn);

				mUnzipTask.connect(InstallArchiveActivity.this);
				Log.d(TAG, "executing task with: " + fn + " , " + writeDirectory);
				mUnzipTask.execute(writeDirectory, fn);

			}

		});


		try {
			//Go populate the location by default if it exists. (Note: If we are recreating, this will get overridden
			//in the restore instance state)
			searchForDefault();
		} catch(Exception e) {
			//This is helper code and Android is suuuuuppppeerr touchy about file system stuff, so don't eat it if 
			//something changes
			e.printStackTrace();
			Log.e(LOG_TAG, "Error while trying to get default location");
		}
	}

	protected void onUnzipSuccessful(Integer result) {
		String ref = "jr://archive/coverage610/profile.xml";

		ApplicationRecord newRecord = new ApplicationRecord(PropertyUtils.genUUID().replace("-",""), ApplicationRecord.STATUS_UNINITIALIZED);
		CommCareApp app = new CommCareApp(newRecord);

		ResourceEngineTask<InstallArchiveActivity> task = new ResourceEngineTask<InstallArchiveActivity>(InstallArchiveActivity.this, false, false, app, false, CommCareTask.GENERIC_TASK_ID) {

			@Override
			protected void deliverResult(
					InstallArchiveActivity receiver,
					org.commcare.android.tasks.ResourceEngineTask.ResourceEngineOutcomes result) {
				if(result == ResourceEngineOutcomes.StatusInstalled){
					receiver.reportSuccess(true);
				}

			}

			@Override
			protected void deliverUpdate(
					InstallArchiveActivity receiver, int[]... update) {
				receiver.updateProgress(update[0][0], update[0][1], update[0][2]);

			}

			@Override
			protected void deliverError(
					InstallArchiveActivity receiver, Exception e) {

			}

		};

		task.connect(InstallArchiveActivity.this);

		task.execute(ref);

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


	private static final long MAX_TIME_TO_TRY = 400;
	/**
	 * Go through all of the obvious storage locations where the zip file might be and see if it's there.
	 */
	private void searchForDefault() {
		//We're only gonna spend a certain amount of time trying to find a good match
		long start = System.currentTimeMillis();

		//Get all of the "roots" where stuff might be
		ArrayList<File> roots = new ArrayList<File>();
		//And stage a place to list folders we'll scan
		ArrayList<File> folders = new ArrayList<File>();

		if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED || Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED_READ_ONLY) {
			roots.add(Environment.getExternalStorageDirectory());
		}

		for(String s : FileUtil.getExternalMounts()) {
			roots.add(new File(s));
		}

		//Now add all of our folders from our roots. We're only going one level deep
		for(File r : roots) {
			//Add the root too
			folders.add(r);
			//Now add all subfolders
			for(File f : r.listFiles()) {
				if(f.isDirectory()) {
					folders.add(f);
				}
			}
		}

		File bestMatch = null;
		//Now scan for the actual file
		for(File folder : folders) {
			for(File f : folder.listFiles()) {
				String name = f.getName().toLowerCase();

				//This is just what we expect the file will be called
				if(name.startsWith("commcare") && name.endsWith(".zip")) {

					if(bestMatch == null) {
						bestMatch = f;
					} else {
						//If we have one, take the newer one
						if(bestMatch.lastModified() < f.lastModified()) {
							bestMatch = f;
						}
					}
				}
			}
			//For now, actually, if we have a good match, just bail without finding the "best" one
			if(bestMatch != null) {
				break;
			}
			if(System.currentTimeMillis() - start > MAX_TIME_TO_TRY) {
				Log.i(LOG_TAG, "Had to bail on looking for a default file, was taking too long");
				break;
			}
		}

		if(bestMatch != null) {
			//If we found a good match, awesome! 
			editFileLocation.setText(bestMatch.getAbsolutePath());
		}
	}

	public static String getFolderName(){
		return "/storage/emulated/0/Download/"+ARCHIVE_UNZIP_LOCATION;
	}

}
