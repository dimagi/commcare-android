package org.commcare.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.util.NoLocalizedTextException;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AdvancedActionsActivity extends SessionAwarePreferenceActivity {

    private final static String REPORT_PROBLEM = "report-problem";
    private final static String VALIDATE_MEDIA = "validate-media";
    private final static String WIFI_DIRECT = "wifi-direct";
    private final static String DUMP_FORMS = "manage-sd-card";
    private final static String CONNECTION_TEST = "connection-test";
    private final static String CLEAR_USER_DATA = "clear-user-data";

    public static final int RESULT_DATA_RESET = RESULT_FIRST_USER + 1;

    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(REPORT_PROBLEM, "problem.report.menuitem");
        keyToTitleMap.put(VALIDATE_MEDIA, "home.menu.validate");
        keyToTitleMap.put(WIFI_DIRECT, "home.menu.wifi.direct");
        keyToTitleMap.put(DUMP_FORMS, "home.menu.formdump");
        keyToTitleMap.put(CONNECTION_TEST, "home.menu.connection.diagnostic");
        keyToTitleMap.put(CLEAR_USER_DATA, "clear.user.data");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));
        addPreferencesFromResource(R.xml.advanced_actions);

        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_CC_PREFS);

        setTitle(Localization.get("home.menu.advanced"));

        setupLocalizedText();
        setupButtons();
    }

    private void setupLocalizedText() {
        PreferenceScreen screen = getPreferenceScreen();
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            String key = screen.getPreference(i).getKey();
            if (keyToTitleMap.containsKey(key)) {
                try {
                    String localizedString = Localization.get(keyToTitleMap.get(key));
                    screen.getPreference(i).setTitle(localizedString);
                } catch (NoLocalizedTextException nle) {

                }
            }
        }
    }

    private void setupButtons() {
        Preference serverSettingsButton = findPreference(REPORT_PROBLEM);
        serverSettingsButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_REPORT_PROBLEM);
                startReportActivity();
                return true;
            }
        });

        Preference validateMediaButton = findPreference(VALIDATE_MEDIA);
        validateMediaButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_VALIDATE_MM);
                startValidationActivity();
                return true;
            }
        });

        Preference wifiDirectButton = findPreference(WIFI_DIRECT);
        if (hasP2p()) {
            wifiDirectButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_WIFI_DIRECT);
                    startWifiDirect();
                    return true;
                }
            });
        } else {
            getPreferenceScreen().removePreference(wifiDirectButton);
        }

        Preference dumpFormsButton = findPreference(DUMP_FORMS);
        dumpFormsButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_MANAGE_SD);
                startFormDump();
                return true;
            }
        });

        Preference connectionTestButton = findPreference(CONNECTION_TEST);
        connectionTestButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_CONNECTION_TEST);
                startConnectionTest();
                return true;
            }
        });

        Preference clearDataButton = findPreference(CLEAR_USER_DATA);
        clearDataButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_CLEAR_USER_DATA);
                clearUserData();
                return true;
            }
        });
    }

    private void startReportActivity() {
        Intent i = new Intent(this, ReportProblemActivity.class);
        startActivity(i);
    }

    private void startValidationActivity() {
        Intent i = new Intent(this, CommCareVerificationActivity.class);
        i.putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_SETTINGS, true);
        startActivity(i);
    }

    private void startWifiDirect() {
        // TODO PLM
    }

    private void startFormDump() {
        // TODO PLM
    }

    private boolean hasP2p() {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
    }

    private void startConnectionTest() {
        Intent i = new Intent(this, ConnectionDiagnosticActivity.class);
        startActivity(i);
    }

    private void clearUserData() {
        CommCareApplication._().clearUserData();
        setResult(RESULT_DATA_RESET);
        finish();
    }
}

