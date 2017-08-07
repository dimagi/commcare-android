package org.commcare.preferences;

import android.content.Context;
import android.support.v7.preference.EditTextPreference;
import android.util.AttributeSet;

import org.commcare.dalvik.R;

public class FilePreference extends EditTextPreference {

    public FilePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.file_pref_dialog);
    }
}
