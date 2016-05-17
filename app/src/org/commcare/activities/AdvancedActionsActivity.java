package org.commcare.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.utils.CommCareUtil;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

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
    private final static String FORCE_LOG_SUBMIT = "force-log-submit";
    private final static String RECOVERY_MODE = "recovery-mode";
    private final static String DISABLE_ANALYTICS = "disable-analytics";

    public static final int RESULT_DATA_RESET = RESULT_FIRST_USER + 1;

    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(REPORT_PROBLEM, "problem.report.menuitem");
        keyToTitleMap.put(VALIDATE_MEDIA, "home.menu.validate");
        keyToTitleMap.put(WIFI_DIRECT, "home.menu.wifi.direct");
        keyToTitleMap.put(DUMP_FORMS, "home.menu.formdump");
        keyToTitleMap.put(CONNECTION_TEST, "home.menu.connection.diagnostic");
        keyToTitleMap.put(CLEAR_USER_DATA, "clear.user.data");
        keyToTitleMap.put(FORCE_LOG_SUBMIT, "force.log.submit");
        keyToTitleMap.put(DISABLE_ANALYTICS, "home.menu.disable.analytics");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName((CommCareApplication._().getCurrentApp().getPreferencesFilename()));
        addPreferencesFromResource(R.xml.advanced_actions);

        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_CC_PREFS);

        setTitle(Localization.get("home.menu.advanced"));

        CommCarePreferences.setupLocalizedText(this, keyToTitleMap);
        setupButtons();
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

        Preference forceSubmitButton = findPreference(FORCE_LOG_SUBMIT);
        forceSubmitButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_FORCE_LOG_SUBMISSION);
                CommCareUtil.triggerLogSubmission(AdvancedActionsActivity.this);
                return true;
            }
        });

        Preference recoveryModeButton = findPreference(RECOVERY_MODE);
        recoveryModeButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_RECOVERY_MODE);
                startRecoveryMode();
                return true;
            }
        });

        Preference analyticsButton = findPreference(DISABLE_ANALYTICS);
        if (CommCarePreferences.isAnalyticsEnabled()) {
            recoveryModeButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.LABEL_RECOVERY_MODE);
                    showAnalyticsOptOutDialog();
                    return true;
                }
            });
        } else {
            getPreferenceScreen().removePreference(analyticsButton);
        }
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

    private void startRecoveryMode() {
        Intent i = new Intent(this, RecoveryActivity.class);
        this.startActivity(i);
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

    private void showAnalyticsOptOutDialog() {
        StandardAlertDialog f = new StandardAlertDialog(this,
                Localization.get("analytics.opt.out.title"),
                Localization.get("analytics.opt.out.message"));

        f.setPositiveButton(Localization.get("analytics.disable.button"),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        CommCarePreferences.disableAnalytics();
                    }
                });

        f.setNegativeButton(Localization.get("option.cancel"),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        f.showNonPersistentDialog();
    }
}

