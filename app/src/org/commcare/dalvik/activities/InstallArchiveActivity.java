package org.commcare.dalvik.activities;

import java.io.File;

import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.UiElement;
import org.commcare.android.references.ArchiveFileRoot;
import org.commcare.android.tasks.UnzipTask;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * @author wspride
 *
 */

@ManagedUi(R.layout.screen_multimedia_inflater)
public class InstallArchiveActivity extends CommCareActivity<InstallArchiveActivity> {
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
    Button btnInstallArchive;

    boolean done = false;

    public static String TAG = "install-archive";
    
    public static String ARCHIVE_REFERENCE = "archive-ref";

    private String currentRef;
    private String targetDirectory;

    /* (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#onCreate(android.os.Bundle)
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        btnFetchFiles.setOnClickListener(new OnClickListener() {
            /*
             * (non-Javadoc)
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {
                //Go fetch us a file path!
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("file/*");
                try {
                    startActivityForResult(intent, REQUEST_FILE_LOCATION);
                } catch(ActivityNotFoundException e) {
                    Toast.makeText(InstallArchiveActivity.this, Localization.get("archive.install.no.browser"), Toast.LENGTH_LONG).show();
                    txtDisplayPrompt.setText(Localization.get("archive.install.no.browser"));
                }
            }
        });

        btnInstallArchive.setOnClickListener(new OnClickListener() {
            /*
             * (non-Javadoc)
             * @see android.view.View.OnClickListener#onClick(android.view.View)
             */
            @Override
            public void onClick(View v) {

                InstallArchiveActivity.this.createArchive(editFileLocation.getText().toString());

            }

        });
    }
    
    public void fireOnceOnStart(){
        if(this.getIntent().hasExtra(InstallArchiveActivity.ARCHIVE_REFERENCE)) {
            editFileLocation.setText(this.getIntent().getStringExtra(InstallArchiveActivity.ARCHIVE_REFERENCE));
            InstallArchiveActivity.this.createArchive(editFileLocation.getText().toString());
        }
    }

    public void createArchive(String filepath){
        currentRef = filepath;

        UnzipTask<InstallArchiveActivity> mUnzipTask = new UnzipTask<InstallArchiveActivity>() {

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverResult(java.lang.Object, java.lang.Object)
             */
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

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverUpdate(java.lang.Object, java.lang.Object[])
             */
            @Override
            protected void deliverUpdate(InstallArchiveActivity receiver, String... update) {
                Log.d(TAG, "delivering unzip upate");
                //
                receiver.updateProgress(update[0], UnzipTask.UNZIP_TASK_ID);
                receiver.txtInteractiveMessages.setText(update[0]);
            }

            /*
             * (non-Javadoc)
             * @see org.commcare.android.tasks.templates.CommCareTask#deliverError(java.lang.Object, java.lang.Exception)
             */
            @Override
            protected void deliverError(InstallArchiveActivity receiver, Exception e) {
                Log.d(TAG, "unzip deliver error: " + e.getMessage());
                receiver.txtInteractiveMessages.setText(Localization.get("archive.install.error", new String[] {e.getMessage()}));
                receiver.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
            }
        };

        String readDir = currentRef;
        File mFile = new File(currentRef);
        String targetDirectory = getTargetFolder();
        FileUtil.deleteFileOrDir(targetDirectory);

        mUnzipTask.connect(InstallArchiveActivity.this);
        Log.d(TAG, "executing task with: " + targetDirectory + " , " + readDir);
        mUnzipTask.execute(readDir, targetDirectory);

    }

    protected void onUnzipSuccessful(Integer result) {

        ArchiveFileRoot afr = CommCareApplication._().getArchiveFileRoot();
        String mGUID = afr.addArchiveFile(getTargetFolder());

        String ref = "jr://archive/" + mGUID + "/profile.ccpr";

        Intent i = new Intent(getIntent());
        i.putExtra(InstallArchiveActivity.ARCHIVE_REFERENCE, ref);
        setResult(RESULT_OK, i);
        finish();

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

    private void evalState() {
        if(done) {
            txtInteractiveMessages.setText(Localization.get("archive.install.state.done"));
            this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
            btnInstallArchive.setEnabled(false);
            return;
        }

        String location = editFileLocation.getText().toString();
        if("".equals(location)) {
            txtInteractiveMessages.setText(Localization.get("archive.install.state.empty"));
            this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
            btnInstallArchive.setEnabled(false);
            return;
        }

        if(!(new File(location)).exists()) {
            txtInteractiveMessages.setText(Localization.get("archive.install.state.invalid.path"));
            this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
            btnInstallArchive.setEnabled(false);
            return;
        }

        else {
            txtInteractiveMessages.setText(Localization.get("archive.install.state.ready"));
            this.TransplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
            btnInstallArchive.setEnabled(true);
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

    public String getTargetFolder(){
        if(targetDirectory != null){
            return targetDirectory;
        }
        
        targetDirectory = CommCareApplication._().getAndroidFsTemp() + PropertyUtils.genUUID();
        return targetDirectory;
    }
    
    
    /*
     * (non-Javadoc)
     * @see org.commcare.android.framework.CommCareActivity#generateProgressDialog(int)
     * 
     * Implementation of generateProgressDialog() for DialogController -- other methods
     * handled entirely in CommCareActivity
     */
    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId == UnzipTask.UNZIP_TASK_ID) {
            String title = Localization.get("archive.install.title");
            String message = Localization.get("archive.install.unzip");
            return CustomProgressDialog.newInstance(title, message, taskId);
        }
        else {
            System.out.println("WARNING: taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in InstallArchiveActivity");
            return null;
        }
    }

}
