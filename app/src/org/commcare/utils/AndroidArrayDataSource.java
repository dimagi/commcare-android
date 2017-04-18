package org.commcare.utils;

import android.content.Context;

import org.commcare.util.ArrayDataSource;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

/**
 * ArrayDataSource that uses the Android 'values' resources
 */

public class AndroidArrayDataSource implements ArrayDataSource {

    private final Context context;

    public AndroidArrayDataSource(Context context){
        this.context = context;
    }

    @Override
    public String[] getArray(String key) {
        try {
            return Localization.getArray(key);
        } catch (NoLocalizedTextException e){
            //default to Android resources
        }
        int resourceId = context.getResources().getIdentifier(key, "array", context.getPackageName());
        if (resourceId == 0) {
            throw new RuntimeException("Localized array data for '" + key + "' not found");
        }
        return context.getResources().getStringArray(resourceId);
    }
}
