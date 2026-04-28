package org.commcare;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import org.commcare.core.services.ICommCarePreferenceManager;

public class AndroidPreferenceManager implements ICommCarePreferenceManager {

    @Override
    public void putLong(String key, long value) {
        SharedPreferences preferences = getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    @Override
    public long getLong(String key, long defaultValue) {
        return getPreferences().getLong(key, defaultValue);
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance());
    }
}
