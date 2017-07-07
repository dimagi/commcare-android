package org.commcare.preferences;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.support.annotation.NonNull;
import android.support.v7.preference.ListPreference;

import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.commcare.google.services.analytics.GoogleAnalyticsFields;
import org.commcare.google.services.analytics.GoogleAnalyticsUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yanokwa
 */

public class FormEntryPreferences extends CommCarePreferenceFragment
        implements OnSharedPreferenceChangeListener {

    public static final String KEY_FONT_SIZE = "font_size";
    public static final String KEY_HELP_MODE_TRAY = "help_mode_tray";

    private final static Map<String, String> prefKeyToAnalyticsEvent = new HashMap<>();

    static {
        prefKeyToAnalyticsEvent.put(KEY_FONT_SIZE, GoogleAnalyticsFields.LABEL_FONT_SIZE);
        prefKeyToAnalyticsEvent.put(KEY_HELP_MODE_TRAY, GoogleAnalyticsFields.LABEL_INLINE_HELP);
    }

    @NonNull
    @Override
    protected String getTitle() {
        return getString(R.string.application_name) + " > " + getString(R.string.form_entry_settings);
    }

    @NonNull
    @Override
    protected String getAnalyticsCategory() {
        return GoogleAnalyticsFields.CATEGORY_FORM_ENTRY;
    }

    @Override
    protected Map<String, String> getPrefKeyAnalyticsEventMap() {
        return prefKeyToAnalyticsEvent;
    }

    @Override
    protected void setupPrefClickListeners() {
        // Nothing to do here
    }

    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return null;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.preferences;
    }

    @Override
    public void onStart() {
        super.onStart();
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
