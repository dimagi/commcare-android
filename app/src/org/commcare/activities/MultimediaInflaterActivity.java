package org.commcare.activities;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
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

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.tasks.MultimediaInflaterTask;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.FileUtil;
import org.commcare.utils.UriToFilePath;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.util.ArrayList;

/**
 * @author ctsims
 */

@ManagedUi(R.layout.screen_multimedia_inflater)
public class MultimediaInflaterActivity extends SessionAwareCommCareActivity<MultimediaInflaterActivity> {
    private static final String TAG = MultimediaInflaterActivity.class.getSimpleName();

    private static final String LOG_TAG = "CC-MultimediaInflator";

    public static final int REQUEST_FILE_LOCATION = 1;

    public static final String EXTRA_FILE_DESTINATION = "ccodk_mia_filedest";

    @UiElement(value = R.id.screen_multimedia_inflater_prompt, locale = "mult.install.prompt")
    TextView txtDisplayPrompt;

    @UiElement(value = R.id.screen_multimedia_install_messages, locale = "mult.install.state.empty")
    TextView txtInteractiveMessages;

    @UiElement(R.id.screen_multimedia_inflater_location)
    EditText editFileLocation;

    @UiElement(R.id.screen_multimedia_inflater_filefetch)
    ImageButton btnFetchFiles;

    @UiElement(value = R.id.screen_multimedia_inflater_install, locale = "mult.install.button")
    Button btnInstallMultimedia;

    private boolean done = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        final String destination = this.getIntent().getStringExtra(EXTRA_FILE_DESTINATION);

        super.onCreate(savedInstanceState);
        btnFetchFiles.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                //Go fetch us a file path!
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                // only allow look for zip files
                intent.setType("application/zip");
                try {
                    startActivityForResult(intent, REQUEST_FILE_LOCATION);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MultimediaInflaterActivity.this, Localization.get("mult.install.no.browser"), Toast.LENGTH_LONG).show();
                }
            }
        });

        btnInstallMultimedia.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                MultimediaInflaterTask<MultimediaInflaterActivity> task = new MultimediaInflaterTask<MultimediaInflaterActivity>() {

                    @Override
                    protected void deliverResult(MultimediaInflaterActivity receiver, Boolean result) {
                        if (result == Boolean.TRUE) {
                            receiver.done = true;
                            receiver.evalState();
                            receiver.setResult(Activity.RESULT_OK);
                            receiver.finish();
                        } else {
                            //assume that we've already set the error message, but make it look scary
                            receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                        }
                    }

                    @Override
                    protected void deliverUpdate(MultimediaInflaterActivity receiver, String... update) {
                        receiver.updateProgress(update[0], CommCareTask.GENERIC_TASK_ID);
                        receiver.txtInteractiveMessages.setText(update[0]);
                    }

                    @Override
                    protected void deliverError(MultimediaInflaterActivity receiver, Exception e) {
                        receiver.txtInteractiveMessages.setText(Localization.get("mult.install.error", new String[]{e.getMessage()}));
                        receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                    }
                };

                task.connect(MultimediaInflaterActivity.this);
                task.executeParallel(editFileLocation.getText().toString(), destination);
            }

        });


        try {
            //Go populate the location by default if it exists. (Note: If we are recreating, this will get overridden
            //in the restore instance state)
            searchForDefault();
        } catch (Exception e) {
            //This is helper code and Android is suuuuuppppeerr touchy about file system stuff, so don't eat it if 
            //something changes
            e.printStackTrace();
            Log.e(LOG_TAG, "Error while trying to get default location");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_FILE_LOCATION) {
            if (resultCode == Activity.RESULT_OK) {
                // Android versions 4.4 and up sometimes don't return absolute
                // filepaths from the file chooser. So resolve the URI into a
                // valid file path.
                Uri uriPath = intent.getData();
                if (uriPath == null) {
                    // issue getting the filepath uri from file browser callout
                    // result
                    Toast.makeText(this,
                            Localization.get("mult.install.state.invalid.path"),
                            Toast.LENGTH_SHORT).show();
                } else {
                    String filePath = 
                        UriToFilePath.getPathFromUri(CommCareApplication._(), uriPath);
                    if (filePath != null) {
                        editFileLocation.setText(filePath);
                    }
                }
            }
        }
    }

    @Override
    protected void onResumeSessionSafe() {
        evalState();
    }

    private void evalState() {
        if (done) {
            txtInteractiveMessages.setText(Localization.get("mult.install.state.done"));
            this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
            btnInstallMultimedia.setEnabled(false);
            return;
        }

        String location = editFileLocation.getText().toString();

        if ("".equals(location)) {
            txtInteractiveMessages.setText(Localization.get("mult.install.state.empty"));
            this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
            btnInstallMultimedia.setEnabled(false);
            return;
        }

        if (!(new File(location)).exists()) {
            txtInteractiveMessages.setText(Localization.get("mult.install.state.invalid.path"));
            this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
            btnInstallMultimedia.setEnabled(false);
        } else {
            txtInteractiveMessages.setText(Localization.get("mult.install.state.ready"));
            this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
            btnInstallMultimedia.setEnabled(true);
        }
    }

    @Override
    public void taskCancelled() {
        txtInteractiveMessages.setText(Localization.get("mult.install.cancelled"));
        this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
    }


    private static final long MAX_TIME_TO_TRY = 400;

    /**
     * Go through all of the obvious storage locations where the zip file might be and see if it's there.
     */
    private void searchForDefault() {
        //We're only gonna spend a certain amount of time trying to find a good match
        long start = System.currentTimeMillis();

        //Get all of the "roots" where stuff might be
        ArrayList<File> roots = new ArrayList<>();
        //And stage a place to list folders we'll scan
        ArrayList<File> folders = new ArrayList<>();

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState())) {
            roots.add(Environment.getExternalStorageDirectory());
        }

        for (String s : FileUtil.getExternalMounts()) {
            roots.add(new File(s));
        }

        //Now add all of our folders from our roots. We're only going one level deep
        for (File r : roots) {
            //Add the root too
            folders.add(r);
            //Now add all subfolders
            for (File f : r.listFiles()) {
                if (f.isDirectory()) {
                    folders.add(f);
                }
            }
        }

        File bestMatch = null;
        //Now scan for the actual file
        for (File folder : folders) {
            for (File f : folder.listFiles()) {
                String name = f.getName().toLowerCase();

                //This is just what we expect the file will be called
                if (name.startsWith("commcare") && name.endsWith(".zip")) {

                    if (bestMatch == null) {
                        bestMatch = f;
                    } else {
                        //If we have one, take the newer one
                        if (bestMatch.lastModified() < f.lastModified()) {
                            bestMatch = f;
                        }
                    }
                }
            }
            //For now, actually, if we have a good match, just bail without finding the "best" one
            if (bestMatch != null) {
                break;
            }
            if (System.currentTimeMillis() - start > MAX_TIME_TO_TRY) {
                Log.i(LOG_TAG, "Had to bail on looking for a default file, was taking too long");
                break;
            }
        }

        if (bestMatch != null) {
            //If we found a good match, awesome! 
            editFileLocation.setText(bestMatch.getAbsolutePath());
        }
    }

    /**
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId == CommCareTask.GENERIC_TASK_ID) {
            String title = Localization.get("mult.install.title");
            String message = Localization.get("mult.install.progress", new String[]{"0"});
            return CustomProgressDialog.newInstance(title, message, taskId);
        }
        Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                + "any valid possibilities in MultiMediaInflaterActivity");
        return null;
    }
}
