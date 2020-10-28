package org.commcare.preferences;

import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCarePreferenceActivity;
import org.commcare.activities.DataChangeLogsActivity;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;

/**
 * @author Aliza Stone (astone@dimagi.com), created 6/9/16.
 */
public class AppManagerAdvancedPreferences extends CommCarePreferenceFragment {

    private final static String CLEAR_USER_DATA = "clear-user-data";
    private final static String DATA_CHANGE_LOGS = "data-change-logs";
    private final static String DEVELOPER_OPTIONS = "developer-options";

    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(CLEAR_USER_DATA, "clear.user.data");
        keyToTitleMap.put(DATA_CHANGE_LOGS, "menu.data.change.logs");
        keyToTitleMap.put(DEVELOPER_OPTIONS, "app.manager.advanced.developer.options");
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
        Preference clearUserDataButton = findPreference(CLEAR_USER_DATA);
        clearUserDataButton.setEnabled(!"".equals(CommCareApplication.instance().getCurrentUserId()));

        clearUserDataButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.CLEAR_USER_DATA);
            AdvancedActionsPreferences.clearUserData((AppCompatActivity)getActivity());
            return true;

        });

        Preference dataChangeLogs = findPreference(DATA_CHANGE_LOGS);
        dataChangeLogs.setOnPreferenceClickListener(preference -> {
            launchDataChangeLogsActivity();
            return true;
        });

        Preference devOptionsButton = findPreference(DEVELOPER_OPTIONS);
        if (AppManagerDeveloperPreferences.isDeveloperPreferencesEnabled()) {
            devOptionsButton.setOnPreferenceClickListener(preference -> {
                FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                        AnalyticsParamValue.APP_MANAGER_DEVELOPER_OPTIONS);
                Intent i = new Intent(getActivity(), CommCarePreferenceActivity.class);
                i.putExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE, CommCarePreferenceActivity.PREF_TYPE_APP_MANAGER_DEVELOPER);
                startActivity(i);
                return true;
            });
        } else {
            getPreferenceScreen().removePreference(devOptionsButton);
        }
    }

    private void launchDataChangeLogsActivity() {
        Intent i = new Intent(getActivity(), DataChangeLogsActivity.class);
        startActivity(i);
    }
}
