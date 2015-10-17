package org.odk.collect.android.preferences;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;

import org.commcare.android.framework.SessionAwarePreferenceActivity;
import org.commcare.dalvik.R;

/**
 * @author yanokwa
 */
public class PreferencesActivity extends SessionAwarePreferenceActivity implements
        OnSharedPreferenceChangeListener {

    public static final String KEY_FONT_SIZE = "font_size";

    public static final String KEY_COMPLETED_DEFAULT = "default_completed";
    public static final String KEY_HELP_MODE_TRAY = "help_mode_tray";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        setTitle(getString(R.string.application_name) + " > " + getString(R.string.general_preferences));

        updateFontSize();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        updateFontSize();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        switch (key) {
            case KEY_FONT_SIZE:
                updateFontSize();
                break;
        }
    }

    private void updateFontSize() {
        ListPreference lp = (ListPreference) findPreference(KEY_FONT_SIZE);
        lp.setSummary(lp.getEntry());
    }
}
