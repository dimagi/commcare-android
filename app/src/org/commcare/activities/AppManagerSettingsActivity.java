package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;

import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.GlobalPrivilegesManager;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by amstone326 on 6/9/16.
 */
public class AppManagerSettingsActivity extends SessionAwarePreferenceActivity {

    private final static String AUTHENTICATE_AS_SUPERUSER = "authenticate-as-superuser";

    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(AUTHENTICATE_AS_SUPERUSER, "menu.superusuer.auth");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.app_manager_preferences);
        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_APP_MANAGER);
        setupUI();
    }

    private void setupUI() {
        setTitle(Localization.get("settings.app.manager.title"));
        CommCarePreferences.addBackButtonToActionBar(this);
        CommCarePreferences.setupLocalizedText(this, keyToTitleMap);
        setupButtons();
    }

    private void setupButtons() {
        Preference superuserAuthButton = findPreference(AUTHENTICATE_AS_SUPERUSER);
        superuserAuthButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_SUPERUSER_AUTH);
                launchSuperuserAuth();
                return true;
            }
        });
    }

    private void launchSuperuserAuth() {
        Intent i = new Intent(this, GlobalPrivilegeClaimingActivity.class);
        i.putExtra(GlobalPrivilegeClaimingActivity.KEY_PRIVILEGE_NAME,
                GlobalPrivilegesManager.PRIVILEGE_SUPERUSER);
        startActivity(i);
    }
}
