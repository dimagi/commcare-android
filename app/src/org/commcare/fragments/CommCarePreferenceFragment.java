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

public abstract class CommCarePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = CommCarePreferenceFragment.class.getSimpleName();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_CC_PREFS);
        setupUI();
        initPrefs();
        loadPrefs();
    }

    @CallSuper
    protected void initPrefs() {
        if(isAppLevelPreference()) {
            PreferenceManager prefMgr = getPreferenceManager();
            prefMgr.setSharedPreferencesName((CommCareApplication.instance().getCurrentApp().getPreferencesFilename()));
        }
    }

    /**
     *
     * @return whether the preference should be stored in app specific preference file.
     */
    protected boolean isAppLevelPreference() {
        return false;
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
    protected void setupUI() {
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
    public void onStart() {
        super.onStart();
        // register the preference change listener
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
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


    @NonNull
    protected abstract String getTitle();

    @Nullable
    protected abstract Map<String, String> getPrefKeyAnalyticsEventMap();

    protected abstract void setupPrefClickListeners();

    @Nullable
    protected abstract Map<String, String> getPrefKeyTitleMap();

    @NonNull
    protected abstract int getPreferencesResource();

    @Override
    public abstract void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key);
}
