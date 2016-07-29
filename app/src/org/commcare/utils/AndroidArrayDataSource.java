package org.commcare.utils;

import android.content.Context;
import android.content.res.Resources;

import org.commcare.dalvik.R;
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
        if(key.contains("ethiopian")){
            return context.getResources().getStringArray(R.array.ethiopian_months);
        } else if(key.contains("nepali")){
            return context.getResources().getStringArray(R.array.nepali_months);
        }
        throw new Resources.NotFoundException("Couldn't find Android array for key " + key);
    }
}
