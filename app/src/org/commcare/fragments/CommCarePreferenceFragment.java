package org.commcare.fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.FilePreference;
import org.commcare.preferences.FilePreferenceDialogFragmentCompat;
import org.commcare.utils.TemplatePrinterUtils;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import java.util.Map;

/**
 * Common PreferenceFragment class from which all other preferences extend.
 */
public abstract class CommCarePreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = CommCarePreferenceFragment.class.getSimpleName();

    private static final String DIALOG_FRAGMENT_TAG =
            "android.support.v7.preference.PreferenceFragment.DIALOG";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        FirebaseAnalyticsUtil.reportPreferenceActivityEntry(this.getClass());
        setTitle();
        initPrefsFile();
        loadPrefs();
        conditionallyHideSpecificPrefs();
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
        setupPrefClickListeners();
        setupLocalizedText();
    }

    protected void conditionallyHideSpecificPrefs() {
        // default implementation does nothing
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
    public void onDisplayPreferenceDialog(Preference preference) {
        // check if dialog is already showing
        if (getFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        if (preference instanceof FilePreference) {
            DialogFragment f = FilePreferenceDialogFragmentCompat.newInstance(preference.getKey());
            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // unregister the preference change listener
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Utility function to request a file using a file browser
     * @param fragment Fragment implementing onActivityResult for this file request
     * @param requestCode Request code to fire the file fetch intent with
     * @param errorTitle Title of the error dialogue that appears if no file browser is installed
     */
    public static void startFileBrowser(Fragment fragment, int requestCode, String errorTitle) {
        Intent chooseTemplateIntent = new Intent()
                .setAction(Intent.ACTION_GET_CONTENT)
                .setType("file/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
        try {
            fragment.startActivityForResult(chooseTemplateIntent, requestCode);
        } catch (ActivityNotFoundException e) {
            // Means that there is no file browser installed on the device
            TemplatePrinterUtils.showAlertDialog(fragment.getActivity(), Localization.get(errorTitle),
                    Localization.get("no.file.browser"), false);
        }
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

    protected abstract void setupPrefClickListeners();

    @Nullable
    protected abstract Map<String, String> getPrefKeyTitleMap();

    protected abstract int getPreferencesResource();

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        String prefValue = sharedPreferences.getString(key, null);
        if (prefValue != null) {
            FirebaseAnalyticsUtil.reportEditPreferenceItem(key, prefValue);
        }
    }
}
