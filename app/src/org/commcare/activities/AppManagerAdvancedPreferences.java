package org.commcare.activities;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.preferences.AdvancedActionsPreferences;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class AppManagerAdvancedPreferences extends CommCarePreferenceFragment {

    private final static String ENABLE_PRIVILEGE = "enable-mobile-privilege";
    private final static String CLEAR_USER_DATA = "clear-user-data";

    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(ENABLE_PRIVILEGE, "menu.enable.privileges");
        keyToTitleMap.put(CLEAR_USER_DATA, "clear.user.data");
    }

    @NonNull
    @Override
    protected String getTitle() {
        return Localization.get("app.manager.advanced.settings.title");
    }

    @Nullable
    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return keyToTitleMap;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.app_manager_preferences;
    }

    @Override
    protected void setupPrefClickListeners() {
        Preference enablePrivilegesButton = findPreference(ENABLE_PRIVILEGE);
        enablePrivilegesButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                        AnalyticsParamValue.ENABLE_PRIVILEGES);
                launchPrivilegeClaimActivity();
                return true;
            }
        });

        Preference clearUserDataButton = findPreference(CLEAR_USER_DATA);
        clearUserDataButton.setEnabled(!"".equals(CommCareApplication.instance().getCurrentUserId()));
        clearUserDataButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                        AnalyticsParamValue.CLEAR_USER_DATA);
                AdvancedActionsPreferences.clearUserData((AppCompatActivity)getActivity());
                return true;
            }
        });
    }

    private void launchPrivilegeClaimActivity() {
        Intent i = new Intent(getActivity(), GlobalPrivilegeClaimingActivity.class);
        startActivity(i);
    }
}
