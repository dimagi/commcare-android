package org.commcare.utils;

import android.content.Context;

import org.commcare.util.ArrayDataSource;

/**
 * ArrayDataSource that uses the Android 'values' resources
 */

public class AndroidArrayDataSource implements ArrayDataSource {

    private Context context;

    public AndroidArrayDataSource(Context context){
        this.context = context;
    }

    @Override
    public String[] getArray(String key) {
        int resourceId = context.getResources().getIdentifier(key, "array", context.getPackageName());

        if (resourceId == 0) {
            throw new RuntimeException("Localized array data for '" + key + "' not found");
        }

        return context.getResources().getStringArray(resourceId);
    }
}
