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
import org.commcare.utils.UriToFilePath;
import org.javarosa.core.services.locale.Localization;

import java.io.File;

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
        mEditText = (EditText)view.findViewById(android.R.id.edit);
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
                String filePath = UriToFilePath.getPathFromUri(CommCareApplication.instance(), uri);
                validateFile(filePath);
            } else {
                //No file selected
                Toast.makeText(getActivity(), Localization.get("file.not.selected"), Toast.LENGTH_LONG).show();
            }
        }
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
