package org.commcare.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.EditTextPreference;
import android.util.AttributeSet;

import org.commcare.dalvik.R;

public class FilePreference extends EditTextPreference {

    private String fileType;

    public FilePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setParams(context, attrs);
    }

    public void setParams(Context context, AttributeSet attrs) {
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FilePreference, 0, 0);
        if (a.hasValue(R.styleable.FilePreference_file_type)) {
            fileType = a.getString(R.styleable.FilePreference_file_type);
        }
        a.recycle();
        setDialogLayoutResource(R.layout.file_pref_dialog);
    }

    public String getFileType() {
        return fileType;
    }
}
