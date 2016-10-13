package org.commcare.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.view.MenuItem;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.logging.analytics.GoogleAnalyticsFields;
import org.commcare.logging.analytics.GoogleAnalyticsUtils;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.DevSessionRestorer;
import org.commcare.tasks.DumpTask;
import org.commcare.tasks.SendTask;
import org.commcare.tasks.WipeTask;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

/**
 * Menu for launching advanced CommCare actions
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AdvancedActionsActivity extends SessionAwarePreferenceActivity {

    private final static String WIFI_DIRECT = "wifi-direct";
    private final static String DUMP_FORMS = "manage-sd-card";
    private final static String REPORT_PROBLEM = "report-problem";
    private final static String FORCE_LOG_SUBMIT = "force-log-submit";
    private final static String VALIDATE_MEDIA = "validate-media";
    private final static String CONNECTION_TEST = "connection-test";
    private final static String RECOVERY_MODE = "recovery-mode";
    private final static String CLEAR_USER_DATA = "clear-user-data";
    private final static String CLEAR_SAVED_SESSION = "clear-saved-session";

    private final static int WIFI_DIRECT_ACTIVITY = 1;
    private final static int DUMP_FORMS_ACTIVITY = 2;

    public final static int RESULT_DATA_RESET = CommCareHomeActivity.RESULT_RESTART + 1;
    public final static int RESULT_FORMS_PROCESSED = CommCareHomeActivity.RESULT_RESTART + 2;

    public final static String FORM_PROCESS_COUNT_KEY = "forms-processed-count";
    public final static String FORM_PROCESS_MESSAGE_KEY = "forms-processed-message";
    static final String KEY_NUMBER_DUMPED = "num_dumped";

    private final static Map<String, String> keyToTitleMap = new HashMap<>();

    static {
        keyToTitleMap.put(REPORT_PROBLEM, "problem.report.menuitem");
        keyToTitleMap.put(VALIDATE_MEDIA, "home.menu.validate");
        keyToTitleMap.put(WIFI_DIRECT, "home.menu.wifi.direct");
        keyToTitleMap.put(DUMP_FORMS, "home.menu.formdump");
        keyToTitleMap.put(CONNECTION_TEST, "home.menu.connection.diagnostic");
        keyToTitleMap.put(CLEAR_USER_DATA, "clear.user.data");
        keyToTitleMap.put(FORCE_LOG_SUBMIT, "force.log.submit");
        keyToTitleMap.put(RECOVERY_MODE, "recovery.mode");
        keyToTitleMap.put(CLEAR_SAVED_SESSION, "menu.clear.saved.session");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.advanced_actions);

        GoogleAnalyticsUtils.reportPrefActivityEntry(GoogleAnalyticsFields.CATEGORY_ADVANCED_ACTIONS);

        setupUI();
    }

    private void setupUI() {
        setTitle(Localization.get("settings.advanced.title"));
        CommCarePreferences.addBackButtonToActionBar(this);

        CommCarePreferences.setupLocalizedText(this, keyToTitleMap);
        setupButtons();
    }

    private void setupButtons() {
        Preference serverSettingsButton = findPreference(REPORT_PROBLEM);
        serverSettingsButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_REPORT_PROBLEM);
                startReportActivity();
                return true;
            }
        });

        Preference validateMediaButton = findPreference(VALIDATE_MEDIA);
        validateMediaButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_VALIDATE_MEDIA);
                startValidationActivity();
                return true;
            }
        });

        Preference wifiDirectButton = findPreference(WIFI_DIRECT);
        if (hasP2p()) {
            wifiDirectButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_WIFI_DIRECT);
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
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_MANAGE_SD);
                startFormDump();
                return true;
            }
        });

        Preference connectionTestButton = findPreference(CONNECTION_TEST);
        connectionTestButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_CONNECTION_TEST);
                startConnectionTest();
                return true;
            }
        });

        Preference clearDataButton = findPreference(CLEAR_USER_DATA);
        clearDataButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_CLEAR_USER_DATA);
                clearUserData(AdvancedActionsActivity.this);
                return true;
            }
        });

        Preference clearSavedSessionButton = findPreference(CLEAR_SAVED_SESSION);
        if (DevSessionRestorer.savedSessionPresent()) {
            clearSavedSessionButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_CLEAR_SAVED_SESSION);
                    DevSessionRestorer.clearSession();
                    return true;
                }
            });
        } else {
            getPreferenceScreen().removePreference(clearSavedSessionButton);
        }


        Preference forceSubmitButton = findPreference(FORCE_LOG_SUBMIT);
        forceSubmitButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_FORCE_LOG_SUBMISSION);
                CommCareUtil.triggerLogSubmission(AdvancedActionsActivity.this);
                return true;
            }
        });

        Preference recoveryModeButton = findPreference(RECOVERY_MODE);
        recoveryModeButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                GoogleAnalyticsUtils.reportAdvancedActionItemClick(GoogleAnalyticsFields.ACTION_RECOVERY_MODE);
                startRecoveryMode();
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
        Intent i = new Intent(this, CommCareWiFiDirectActivity.class);
        startActivityForResult(i, WIFI_DIRECT_ACTIVITY);
    }

    private void startFormDump() {
        Intent i = new Intent(this, CommCareFormDumpActivity.class);
        i.putExtra(CommCareFormDumpActivity.EXTRA_FILE_DESTINATION, CommCareApplication._().getCurrentApp().storageRoot());
        startActivityForResult(i, DUMP_FORMS_ACTIVITY);
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

    public static void clearUserData(final Activity activity) {
        StandardAlertDialog d =
                new StandardAlertDialog(activity,
                        Localization.get("clear.user.data.warning.title"),
                        Localization.get("clear.user.data.warning.message"));
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog,
                                int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    CommCareApplication._().clearUserData();
                    activity.setResult(RESULT_DATA_RESET);
                    activity.finish();
                }
                dialog.dismiss();
            }
        };
        d.setPositiveButton(StringUtils.getStringRobust(activity, R.string.ok), listener);
        d.setNegativeButton(StringUtils.getStringRobust(activity, R.string.cancel), listener);
        d.showNonPersistentDialog();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == WIFI_DIRECT_ACTIVITY || requestCode == DUMP_FORMS_ACTIVITY) {
            String messageKey = getBulkFormMessageKey(resultCode);
            if (messageKey == null) {
                return;
            }

            int dumpedCount = intent.getIntExtra(KEY_NUMBER_DUMPED, -1);
            Intent returnIntent = new Intent();
            returnIntent.putExtra(FORM_PROCESS_COUNT_KEY, dumpedCount);
            returnIntent.putExtra(FORM_PROCESS_MESSAGE_KEY, messageKey);
            setResult(RESULT_FORMS_PROCESSED, returnIntent);
            finish();
        }
    }

    private static String getBulkFormMessageKey(int resultCode) {
        if (resultCode == DumpTask.BULK_DUMP_ID) {
            return "bulk.form.dump.success";
        } else if (resultCode == SendTask.BULK_SEND_ID ||
                resultCode == WipeTask.WIPE_TASK_ID ||
                resultCode == SendTask.BULK_SEND_ID) {
            return "bulk.form.send.success";
        } else {
            return null;
        }
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

