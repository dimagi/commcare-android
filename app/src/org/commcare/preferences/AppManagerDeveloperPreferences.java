package org.commcare.preferences;

import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.GlobalPrivilegeClaimingActivity;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

public class AppManagerDeveloperPreferences extends CommCarePreferenceFragment {

    private final static String ENABLE_PRIVILEGE = "enable-mobile-privilege";
    private static final String DEVELOPER_PREFERENCES_ENABLED = "developer-preferences-enabled";
    private final static Map<String, String> keyToTitleMap = new HashMap<>();
    private static final String CONNECT_ID_ENABLED = "connect_id-enabled";


    static {
        keyToTitleMap.put(ENABLE_PRIVILEGE, "menu.enable.privileges");
    }

    @NonNull
    @Override
    protected String getTitle() {
        return Localization.get("app.manager.developer.options.title");
    }

    @Nullable
    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return keyToTitleMap;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.app_manager_developer_preferences;
    }

    @Override
    protected void setupPrefClickListeners() {
        Preference enablePrivilegesButton = findPreference(ENABLE_PRIVILEGE);
        enablePrivilegesButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.ENABLE_PRIVILEGES);
            launchPrivilegeClaimActivity();
            return true;
        });
    }

    private void launchPrivilegeClaimActivity() {
        Intent i = new Intent(getActivity(), GlobalPrivilegeClaimingActivity.class);
        startActivity(i);
    }

    public static void setDeveloperPreferencesEnabled(boolean enabled) {
        GlobalPrivilegesManager.getGlobalPrefsRecord()
                .edit()
                .putBoolean(DEVELOPER_PREFERENCES_ENABLED, enabled)
                .apply();
    }

    public static boolean isDeveloperPreferencesEnabled() {
        return GlobalPrivilegesManager.getGlobalPrefsRecord().getBoolean(DEVELOPER_PREFERENCES_ENABLED, false);
    }

    public static void setConnectIdEnabled(boolean enabled) {
        GlobalPrivilegesManager.getGlobalPrefsRecord()
                .edit()
                .putBoolean(CONNECT_ID_ENABLED, enabled)
                .apply();
    }

    public static boolean isConnectIdEnabled() {
        //NOTE: Setting default case to true for initial user testing, but production should keep the default false
        return GlobalPrivilegesManager.getGlobalPrefsRecord().getBoolean(CONNECT_ID_ENABLED, false);
    }
}
