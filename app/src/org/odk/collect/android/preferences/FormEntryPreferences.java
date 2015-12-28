package org.odk.collect.android.preferences;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;

import org.commcare.android.analytics.GoogleAnalyticsFields;
import org.commcare.android.analytics.GoogleAnalyticsUtils;
import org.commcare.android.framework.SessionAwarePreferenceActivity;
import org.commcare.dalvik.R;
import org.commcare.dalvik.preferences.CommCarePreferences;

/**
 * @author yanokwa
 */
public class FormEntryPreferences extends SessionAwarePreferenceActivity
        implements OnSharedPreferenceChangeListener {

    public static final String KEY_FONT_SIZE = "font_size";
    public static final String KEY_HELP_MODE_TRAY = "help_mode_tray";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_FORM_PREFS);
        setTitle(getString(R.string.application_name) + " > " + getString(R.string.form_entry_settings));
        updateFontSize();
        createPreferenceOnClickListeners();
    }

    private void createPreferenceOnClickListeners() {
        PreferenceManager prefMgr = getPreferenceManager();
        GoogleAnalyticsUtils.createPreferenceOnClickListener(prefMgr,
                GoogleAnalyticsFields.CATEGORY_FORM_PREFS, KEY_FONT_SIZE,
                GoogleAnalyticsFields.LABEL_FONT_SIZE);
        GoogleAnalyticsUtils.createPreferenceOnClickListener(prefMgr,
                GoogleAnalyticsFields.CATEGORY_FORM_PREFS, KEY_HELP_MODE_TRAY,
                GoogleAnalyticsFields.LABEL_INLINE_HELP);
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
                reportEditPreference(GoogleAnalyticsFields.LABEL_FONT_SIZE);
                updateFontSize();
                break;
            case KEY_HELP_MODE_TRAY:
                reportEditPreference(GoogleAnalyticsFields.LABEL_INLINE_HELP);
        }
    }

    private static void reportEditPreference(String label) {
        GoogleAnalyticsUtils.reportEditPref(GoogleAnalyticsFields.CATEGORY_FORM_PREFS, label);
    }

    private void updateFontSize() {
        ListPreference lp = (ListPreference)findPreference(KEY_FONT_SIZE);
        lp.setSummary(lp.getEntry());
    }
}
