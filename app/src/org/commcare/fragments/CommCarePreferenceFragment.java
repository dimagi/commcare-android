package org.commcare.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.google.services.analytics.GoogleAnalyticsFields;
import org.commcare.google.services.analytics.GoogleAnalyticsUtils;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import java.util.Map;

/**
 * Common PreferenceFragment class from which all other preferences extend.
 */
public abstract class CommCarePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = CommCarePreferenceFragment.class.getSimpleName();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        GoogleAnalyticsUtils.reportPrefActivityEntry(getAnalyticsCategory());
        setTitle();
        initPrefsFile();
        loadPrefs();
    }

    @CallSuper
    protected void initPrefsFile() {
        if (isPersistentAppPreference()) {
            PreferenceManager prefMgr = getPreferenceManager();
            prefMgr.setSharedPreferencesName((CommCareApplication.instance().getCurrentApp().getPreferencesFilename()));
        }
    }

    @CallSuper
    protected void loadPrefs() {
        // Add 'general' preferences, defined in the XML file
        addPreferencesFromResource(getPreferencesResource());

        GoogleAnalyticsUtils.createPreferenceOnClickListeners(getPreferenceManager(), getPrefKeyAnalyticsEventMap(),
                GoogleAnalyticsFields.CATEGORY_CC_PREFS);
        setupPrefClickListeners();
        setupLocalizedText();
    }

    @CallSuper
    protected void setTitle() {
        if (getActivity() != null) {
            getActivity().setTitle(getTitle());
        }
    }

    private void setupLocalizedText() {
        Map<String, String> prefToTitleMap = getPrefKeyTitleMap();
        if (prefToTitleMap != null) {
            PreferenceScreen screen = getPreferenceScreen();
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                String key = screen.getPreference(i).getKey();
                if (prefToTitleMap.containsKey(key)) {
                    try {
                        String localizedString = Localization.get(prefToTitleMap.get(key));
                        screen.getPreference(i).setTitle(localizedString);
                    } catch (NoLocalizedTextException nle) {
                        Log.w(TAG, "Unable to localize: " + prefToTitleMap.get(key));
                    }
                }
            }
        }
    }



    @Override
    public void onResume() {
        super.onResume();
        // register the preference change listener
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        // unregister the preference change listener
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Use this to reload prefs screen
     */
    @CallSuper
    protected void reset() {
        getPreferenceScreen().removeAll();
        loadPrefs();
    }

    /**
     * @return whether the preference should be stored in app specific preference file.
     */
    protected boolean isPersistentAppPreference() {
        return false;
    }


    @NonNull
    protected abstract String getTitle();

    @NonNull
    protected abstract String getAnalyticsCategory();

    @Nullable
    protected abstract Map<String, String> getPrefKeyAnalyticsEventMap();

    protected abstract void setupPrefClickListeners();

    @Nullable
    protected abstract Map<String, String> getPrefKeyTitleMap();

    protected abstract int getPreferencesResource();

    @Override
    public abstract void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key);
}
