package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.preferences.CommCarePreferences;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class AppManagerAdvancedSettings extends PreferenceActivity {

    private final static String ENABLE_PRIVILEGE = "enable-mobile-privilege";
    private final static String CLEAR_USER_DATA = "clear-user-data";

    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(ENABLE_PRIVILEGE, "menu.enable.privilege");
        keyToTitleMap.put(CLEAR_USER_DATA, "clear.user.data");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.app_manager_preferences);
        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_APP_MANAGER);
        setupUI();
    }

    private void setupUI() {
        setTitle(Localization.get("app.manager.advanced.settings.title"));
        CommCarePreferences.addBackButtonToActionBar(this);
        CommCarePreferences.setupLocalizedText(this, keyToTitleMap);
        setupButtons();
    }

    private void setupButtons() {
        Preference enablePrivilegesButton = findPreference(ENABLE_PRIVILEGE);
        enablePrivilegesButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_ENABLE_PRIVILEGES);
                launchPrivilegeClaimActivity();
                return true;
            }
        });

        Preference clearUserDataButton = findPreference(CLEAR_USER_DATA);
        clearUserDataButton.setEnabled(!"".equals(CommCareApplication.getInstance().getCurrentUserId()));
        clearUserDataButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_CLEAR_USER_DATA);
                AdvancedActionsActivity.clearUserData(AppManagerAdvancedSettings.this);
                return true;
            }
        });
    }

    private void launchPrivilegeClaimActivity() {
        Intent i = new Intent(this, GlobalPrivilegeClaimingActivity.class);
        startActivity(i);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
