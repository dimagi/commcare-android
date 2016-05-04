package org.commcare.activities;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.engine.references.ArchiveFileRoot;
import org.commcare.tasks.UnzipTask;
import org.commcare.utils.FileUtil;
import org.commcare.utils.UriToFilePath;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.PropertyUtils;

import java.io.File;

/**
 * @author wspride
 */
@ManagedUi(R.layout.screen_multimedia_inflater)
public class InstallArchiveActivity extends CommCareActivity<InstallArchiveActivity> {

    private static final int REQUEST_FILE_LOCATION = 1;

    @UiElement(value = R.id.screen_multimedia_inflater_prompt, locale = "archive.install.prompt")
    private TextView txtDisplayPrompt;

    @UiElement(value = R.id.screen_multimedia_install_messages, locale = "archive.install.state.empty")
    private TextView txtInteractiveMessages;

    @UiElement(R.id.screen_multimedia_inflater_location)
    private EditText editFileLocation;

    @UiElement(R.id.screen_multimedia_inflater_filefetch)
    private ImageButton btnFetchFiles;

    @UiElement(value = R.id.screen_multimedia_inflater_install, locale = "archive.install.button")
    private Button btnInstallArchive;

    public static final String ARCHIVE_REFERENCE = "archive-ref";

    private String targetDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        btnFetchFiles.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //Go fetch us a file path!
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                try {
                    startActivityForResult(intent, REQUEST_FILE_LOCATION);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(InstallArchiveActivity.this, Localization.get("archive.install.no.browser"), Toast.LENGTH_LONG).show();
                    txtDisplayPrompt.setText(Localization.get("archive.install.no.browser"));
                }
            }
        });

        btnInstallArchive.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                InstallArchiveActivity.this.createArchive(editFileLocation.getText().toString());
            }
        });

        // avoid keyboard pop-up
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void createArchive(String filepath) {
        UnzipTask<InstallArchiveActivity> mUnzipTask = new UnzipTask<InstallArchiveActivity>() {
            @Override
            protected void deliverResult(InstallArchiveActivity receiver, Integer result) {
                if (result > 0) {
                    receiver.onUnzipSuccessful();
                } else {
                    //assume that we've already set the error message, but make it look scary
                    receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
                }
            }

            @Override
            protected void deliverUpdate(InstallArchiveActivity receiver, String... update) {
                receiver.updateProgress(update[0], UnzipTask.UNZIP_TASK_ID);
                receiver.txtInteractiveMessages.setText(update[0]);
            }

            @Override
            protected void deliverError(InstallArchiveActivity receiver, Exception e) {
                receiver.txtInteractiveMessages.setText(Localization.get("archive.install.error", new String[]{e.getMessage()}));
                receiver.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
            }
        };

        String targetDirectory = getTargetFolder();
        FileUtil.deleteFileOrDir(targetDirectory);

        mUnzipTask.connect(this);
        mUnzipTask.execute(filepath, targetDirectory);
    }

    private void onUnzipSuccessful() {
        ArchiveFileRoot afr = CommCareApplication._().getArchiveFileRoot();
        String mGUID = afr.addArchiveFile(getTargetFolder());

        String ref = "jr://archive/" + mGUID + "/profile.ccpr";

        Intent i = new Intent(getIntent());
        i.putExtra(InstallArchiveActivity.ARCHIVE_REFERENCE, ref);
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_FILE_LOCATION) {
            if (resultCode == Activity.RESULT_OK) {
                // Android versions 4.4 and up sometimes don't return absolute
                // filepaths from the file chooser. So resolve the URI into a
                // valid file path.
                String filePath = UriToFilePath.getPathFromUri(CommCareApplication._(),
                        intent.getData());
                if (filePath != null) {
                    editFileLocation.setText(filePath);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        evalState();
    }

    private void evalState() {
        String location = editFileLocation.getText().toString();
        if ("".equals(location)) {
            txtInteractiveMessages.setText(Localization.get("archive.install.state.empty"));
            this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
            btnInstallArchive.setEnabled(false);
            return;
        }

        if (!(new File(location)).exists()) {
            txtInteractiveMessages.setText(Localization.get("archive.install.state.invalid.path"));
            this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
            btnInstallArchive.setEnabled(false);
        } else {
            txtInteractiveMessages.setText(Localization.get("archive.install.state.ready"));
            this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification);
            btnInstallArchive.setEnabled(true);
        }
    }

    @Override
    public void taskCancelled() {
        txtInteractiveMessages.setText(Localization.get("archive.install.cancelled"));
        this.transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
    }

    private String getTargetFolder() {
        if (targetDirectory != null) {
            return targetDirectory;
        }

        targetDirectory = CommCareApplication._().getAndroidFsTemp() + PropertyUtils.genUUID();
        return targetDirectory;
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId == UnzipTask.UNZIP_TASK_ID) {
            String title = Localization.get("archive.install.title");
            String message = Localization.get("archive.install.unzip");
            return CustomProgressDialog.newInstance(title, message, taskId);
        } else {
            return super.generateProgressDialog(taskId);
        }
    }
}
