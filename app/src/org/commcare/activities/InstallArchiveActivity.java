package org.commcare.activities;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.engine.references.ArchiveFileRoot;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.tasks.UnZipTaskListener;
import org.commcare.tasks.UnzipTask;
import org.commcare.utils.CczUtils;
import org.commcare.utils.FileUtil;
import org.commcare.utils.ZipUtils;
import org.commcare.views.ManagedUi;
import org.commcare.views.UiElement;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.locale.Localization;

/**
 * @author wspride
 */
@ManagedUi(R.layout.screen_multimedia_inflater)
public class InstallArchiveActivity extends CommCareActivity<InstallArchiveActivity> implements UnZipTaskListener {
    private static final String TAG = InstallArchiveActivity.class.getSimpleName();

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

    public static final String ARCHIVE_FILEPATH = "archive-filepath";
    public static final String ARCHIVE_JR_REFERENCE = "archive-jr-ref";
    public static final String FROM_UPDATE = "from-update-activity";

    private String targetDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean fromUpdateActivity = getIntent().getBooleanExtra(FROM_UPDATE, false);
        if (fromUpdateActivity) {
            txtDisplayPrompt.setText(Localization.get("archive.update.prompt"));
            btnInstallArchive.setText(Localization.get("archive.update.button"));
        }

        btnFetchFiles.setOnClickListener(v -> {
            //Go fetch us a file path!
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            try {
                startActivityForResult(intent, REQUEST_FILE_LOCATION);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(InstallArchiveActivity.this, Localization.get("archive.install.no.browser"), Toast.LENGTH_LONG).show();
                txtDisplayPrompt.setText(Localization.get("archive.install.no.browser"));
            }
        });

        btnInstallArchive.setOnClickListener(v -> {
            String archivePath = editFileLocation.getText().toString();
            HiddenPreferences.setLastKnownCczLocation(archivePath);
            ZipUtils.UnzipFile(this, archivePath, getTargetFolder());
        });

        // avoid keyboard pop-up
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        processProvidedReference();
    }

    private void processProvidedReference() {
        if (getIntent().hasExtra(ARCHIVE_FILEPATH)) {
            ZipUtils.UnzipFile(this, getIntent().getStringExtra(ARCHIVE_FILEPATH), getTargetFolder());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_FILE_LOCATION && resultCode == Activity.RESULT_OK) {
            FileUtil.updateFileLocationFromIntent(this, intent, editFileLocation);
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

        if (!FileUtil.isValidFileLocation(location)) {
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
        targetDirectory = CczUtils.getCczTargetPath();
        return targetDirectory;
    }

    @Override
    public CustomProgressDialog generateProgressDialog(int taskId) {
        if (taskId == UnzipTask.UNZIP_TASK_ID) {
            String title = Localization.get("archive.install.title");
            String message = Localization.get("archive.install.unzip");
            return CustomProgressDialog.newInstance(title, message, taskId);
        } else {
            Log.w(TAG, "taskId passed to generateProgressDialog does not match "
                    + "any valid possibilities in InstallArchiveActivity");
            return null;
        }
    }

    // Unzip Callbacks
    @Override
    public void OnUnzipSuccessful(Integer result) {
        if (result > 0) {
            ArchiveFileRoot afr = CommCareApplication.instance().getArchiveFileRoot();
            String mGUID = afr.addArchiveFile(getTargetFolder());

            String ref = "jr://archive/" + mGUID + "/profile.ccpr";

            Intent i = new Intent(getIntent());
            i.putExtra(InstallArchiveActivity.ARCHIVE_JR_REFERENCE, ref);
            setResult(RESULT_OK, i);
            finish();
        } else {
            //assume that we've already set the error message during update, but make it look scary
            transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
        }
    }

    @Override
    public void OnUnzipFailure(String cause) {
        if (!TextUtils.isEmpty(cause)) {
            txtInteractiveMessages.setText(Localization.get("archive.install.error", new String[]{cause}));
        }
        transplantStyle(txtInteractiveMessages, R.layout.template_text_notification_problem);
    }

    @Override
    public void updateUnzipProgress(String update, int taskId) {
        updateProgress(update, UnzipTask.UNZIP_TASK_ID);
        txtInteractiveMessages.setText(update);
    }
}
