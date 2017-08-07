package org.commcare.preferences;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreferenceDialogFragmentCompat;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.utils.UriToFilePath;

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
            CommCarePreferences.startFileBrowser(FilePreferenceDialogFragmentCompat.this, REQUEST_FILE, "cannot.set.payload");
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_FILE) {
            if (resultCode == RESULT_OK && intent != null) {
                Uri uri = intent.getData();
                String filePath = UriToFilePath.getPathFromUri(CommCareApplication.instance(), uri);
                if (filePath != null) {
                    File f = new File(filePath);
                    if (f != null && f.exists()) {
                        mEditText.setText(filePath);
                    } else {
                        Toast.makeText(getActivity(), "File does not exit", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                //No file selected
                Toast.makeText(getActivity(), "No file selected...", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
