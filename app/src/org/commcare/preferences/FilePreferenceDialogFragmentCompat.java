package org.commcare.preferences;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreferenceDialogFragmentCompat;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.apache.commons.io.FilenameUtils;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileUtil;
import org.commcare.utils.UriToFilePath;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.engine.models.Step;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static android.app.Activity.RESULT_OK;

public class FilePreferenceDialogFragmentCompat extends EditTextPreferenceDialogFragmentCompat {

    public static final int REQUEST_FILE = 1;
    private EditText mEditText;

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
        mEditText = view.findViewById(android.R.id.edit);
        view.findViewById(R.id.filefetch).setOnClickListener(v -> {
            MainConfigurablePreferences.startFileBrowser(FilePreferenceDialogFragmentCompat.this,
                    REQUEST_FILE,
                    Localization.get("no.file.browser.title"));
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_FILE) {
            if (resultCode == RESULT_OK && intent != null) {
                Uri uri = intent.getData();
                String filePath;
                try {
                    filePath = UriToFilePath.getPathFromUri(CommCareApplication.instance(), uri);
                } catch (UriToFilePath.NoDataColumnForUriException e) {
                    filePath = getNewPathFromUri(uri);
                }
                validateFile(filePath);
            } else {
                //No file selected
                Toast.makeText(getActivity(), Localization.get("file.not.selected"), Toast.LENGTH_LONG).show();
            }
        }
    }

    private String getNewPathFromUri(Uri uri) {
        // We can't get access to filePath but only the uri and
        // since we are going to use this path later on in some other
        // activity stack which will not have permissions for this uri
        // we need to copy the file internally and pass the filepath of the new file
        String filePath = getContext().getFilesDir().getAbsolutePath() + "/temp/" + FileUtil.getFileName(uri.toString());
        File file = new File(filePath);
        try {
            InputStream fileStream = getContext().getContentResolver().openInputStream(uri);
            FileUtil.ensureFilePathExists(file);
            FileUtil.copyFile(fileStream, file);
        } catch (IOException e1) {
            e1.printStackTrace();
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Getting new file path from uri failed due to " + e1.getMessage());
            Toast.makeText(getActivity(), Localization.get("file.selection.failed"), Toast.LENGTH_LONG).show();
            return null;
        }
        return filePath;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult && validateFile(mEditText.getText().toString())) {
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
                    if (!mEditText.getText().toString().contentEquals(filePath)) {
                        mEditText.setText(filePath);
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
