package org.commcare.preferences;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.apache.commons.io.FilenameUtils;
import org.commcare.dalvik.R;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import androidx.preference.EditTextPreferenceDialogFragmentCompat;

import static androidx.appcompat.app.AppCompatActivity.RESULT_OK;

public class FilePreferenceDialogFragmentCompat extends EditTextPreferenceDialogFragmentCompat {

    public static final int REQUEST_FILE = 1;
    private EditText mSavedFilePathText;
    private EditText mDisplayText;

    public static FilePreferenceDialogFragmentCompat newInstance(String key) {
        final FilePreferenceDialogFragmentCompat fragment = new FilePreferenceDialogFragmentCompat();
        final Bundle b = new Bundle(1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mSavedFilePathText = view.findViewById(android.R.id.edit);
        mDisplayText = view.findViewById(R.id.display_field);
        view.findViewById(R.id.filefetch).setOnClickListener(v -> {
            MainConfigurablePreferences.startFileBrowser(FilePreferenceDialogFragmentCompat.this,
                    REQUEST_FILE,
                    Localization.get("no.file.browser.title"));
        });
        if (mSavedFilePathText.getText() != null) {
            File f = new File(mSavedFilePathText.getText().toString());
            if (f.exists()) {
                mDisplayText.setText(f.getName());
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_FILE) {
            if (resultCode == RESULT_OK && intent != null) {
                Uri uri = intent.getData();
                if (uri != null) {
                    String fileName = FileUtil.getFileName(getContext(), uri);
                    File destination = new File(getContext().getExternalCacheDir(), fileName);
                    try {
                        InputStream inputStream = getContext().getContentResolver().openInputStream(uri);
                        FileUtil.copyFile(inputStream, destination);
                    } catch (IOException e) {
                        Logger.exception(LogTypes.TYPE_MAINTENANCE, e);
                        Toast.makeText(getActivity(), Localization.get("file.selection.failed"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    validateFile(destination.getAbsolutePath());
                } else {
                    Toast.makeText(getActivity(), Localization.get("file.selection.failed"), Toast.LENGTH_LONG).show();
                }
            } else {
                //No file selected
                Toast.makeText(getActivity(), Localization.get("file.not.selected"), Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getNewPathFromUri(Uri uri) throws IOException {
        // We can't get access to filePath but only the uri and
        // since we are going to use this path later on in some other
        // activity stack which will not have permissions for this uri
        // we need to copy the file internally and pass the filepath of the new file
        String filePath = getContext().getFilesDir().getAbsolutePath() + "/temp/" + FileUtil.getFileName(uri.toString());
        File file = new File(filePath);
        InputStream fileStream = getContext().getContentResolver().openInputStream(uri);
        FileUtil.ensureFilePathExists(file);
        FileUtil.copyFile(fileStream, file);
        return filePath;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult && validateFile(mSavedFilePathText.getText().toString())) {
            super.onDialogClosed(positiveResult);
        }
    }

    /**
     * Checks whether a file with given filepath is a valid file
     *
     * @param filePath filepath of file that needs to be validated
     * @return true if filepath represents a valid file or if filepath is empty, false otherwise.
     */
    private boolean validateFile(String filePath) {
        String fileType = ((FilePreference)getPreference()).getFileType();
        if (filePath != null && !filePath.isEmpty()) {
            if (fileType == null || FilenameUtils.getExtension(filePath).contentEquals(fileType)) {
                File f = new File(filePath);
                if (f.exists()) {
                    if (!mSavedFilePathText.getText().toString().contentEquals(filePath)) {
                        mSavedFilePathText.setText(filePath);
                        mDisplayText.setText(f.getName());
                    }
                    return true;
                } else {
                    Toast.makeText(getActivity(), Localization.get("file.not.exist"), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getActivity(), Localization.get("file.wrong.type", fileType), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getActivity(), Localization.get("file.not.selected"), Toast.LENGTH_LONG).show();
            // We still want to reset the preference to empty file path
            return true;
        }
        return false;
    }
}
