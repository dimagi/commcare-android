package org.commcare.preferences;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareFormDumpActivity;
import org.commcare.activities.CommCareVerificationActivity;
import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.activities.ConnectionDiagnosticActivity;
import org.commcare.activities.HomeScreenBaseActivity;
import org.commcare.activities.RecoveryActivity;
import org.commcare.activities.ReportProblemActivity;
import org.commcare.dalvik.R;
import org.commcare.fragments.CommCarePreferenceFragment;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.tasks.DumpTask;
import org.commcare.tasks.SendTask;
import org.commcare.tasks.WipeTask;
import org.commcare.utils.CommCareUtil;
import org.commcare.utils.StorageUtils;
import org.commcare.utils.StringUtils;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;

import static android.app.Activity.RESULT_CANCELED;

/**
 * Menu for launching advanced CommCare actions
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class AdvancedActionsPreferences extends CommCarePreferenceFragment {

    private final static String WIFI_DIRECT = "wifi-direct";
    private final static String DUMP_FORMS = "manage-sd-card";
    private final static String REPORT_PROBLEM = "report-problem";
    private final static String FORCE_LOG_SUBMIT = "force-log-submit";
    private final static String VALIDATE_MEDIA = "validate-media";
    private final static String CONNECTION_TEST = "connection-test";
    private final static String RECOVERY_MODE = "recovery-mode";
    private final static String CLEAR_USER_DATA = "clear-user-data";
    private final static String CLEAR_SAVED_SESSION = "clear-saved-session";
    private final static String DISABLE_PRE_UPDATE_SYNC = "bypass-pre-update-sync";
    private final static String ENABLE_RATE_LIMIT_POPUP = "enable-rate-limit-popup";
    private final static String ENABLE_MANUAL_FORM_QUARANTINE = "enable-manual-form-quarantine";

    private final static int WIFI_DIRECT_ACTIVITY = 1;
    private final static int DUMP_FORMS_ACTIVITY = 2;
    private final static int REPORT_PROBLEM_ACTIVITY = 3;
    private final static int VALIDATE_MEDIA_ACTIVITY = 4;

    public final static int RESULT_DATA_RESET = HomeScreenBaseActivity.RESULT_RESTART + 1;
    public final static int RESULT_FORMS_PROCESSED = HomeScreenBaseActivity.RESULT_RESTART + 2;

    public final static String FORM_PROCESS_COUNT_KEY = "forms-processed-count";
    public final static String FORM_PROCESS_MESSAGE_KEY = "forms-processed-message";
    public final static String KEY_NUMBER_DUMPED = "num_dumped";

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
        keyToTitleMap.put(DISABLE_PRE_UPDATE_SYNC, "menu.disable.pre.update.sync");
        keyToTitleMap.put(ENABLE_RATE_LIMIT_POPUP, "menu.enable.rate.limit.popup");
        keyToTitleMap.put(ENABLE_MANUAL_FORM_QUARANTINE, "menu.enable.manual.form.quarantine");
    }

    @NonNull
    @Override
    protected String getTitle() {
        return Localization.get("settings.advanced.title");
    }

    @Nullable
    @Override
    protected Map<String, String> getPrefKeyTitleMap() {
        return keyToTitleMap;
    }

    @Override
    protected int getPreferencesResource() {
        return R.xml.advanced_actions;
    }

    @Override
    protected void conditionallyHideSpecificPrefs() {
        Preference reportProblemButton = findPreference(REPORT_PROBLEM);
        if (reportProblemButton != null && DeveloperPreferences.shouldHideReportIssue()) {
            getPreferenceScreen().removePreference(reportProblemButton);
        }
        if (!HiddenPreferences.preUpdateSyncNeeded()) {
            Preference clearAppReleaseTimePref = findPreference(DISABLE_PRE_UPDATE_SYNC);
            if (clearAppReleaseTimePref != null) {
                getPreferenceScreen().removePreference(clearAppReleaseTimePref);
            }
        }
        if (!HiddenPreferences.isRateLimitPopupDisabled()) {
            Preference rateLimitPopup = findPreference(ENABLE_RATE_LIMIT_POPUP);
            if (rateLimitPopup != null) {
                getPreferenceScreen().removePreference(rateLimitPopup);
            }
        }
    }

    @Override
    protected void setupPrefClickListeners() {
        Preference reportProblemButton = findPreference(REPORT_PROBLEM);
        reportProblemButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.REPORT_PROBLEM);
            startReportActivity();
            return true;
        });

        Preference validateMediaButton = findPreference(VALIDATE_MEDIA);
        validateMediaButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.VALIDATE_MEDIA);
            startValidationActivity();
            return true;
        });

        Preference wifiDirectButton = findPreference(WIFI_DIRECT);
        if (hasP2p()) {
            wifiDirectButton.setOnPreferenceClickListener(preference -> {
                FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                        AnalyticsParamValue.WIFI_DIRECT);
                startWifiDirect();
                return true;
            });
        } else {
            getPreferenceScreen().removePreference(wifiDirectButton);
        }

        Preference dumpFormsButton = findPreference(DUMP_FORMS);
        dumpFormsButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.MANAGE_SD);
            startFormDump();
            return true;
        });

        Preference connectionTestButton = findPreference(CONNECTION_TEST);
        connectionTestButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.CONNECTION_TEST);
            startConnectionTest();
            return true;
        });

        Preference clearDataButton = findPreference(CLEAR_USER_DATA);
        clearDataButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.CLEAR_USER_DATA);
            clearUserData((AppCompatActivity)getActivity());
            return true;
        });

        Preference clearSavedSessionButton = findPreference(CLEAR_SAVED_SESSION);
        if (DevSessionRestorer.savedSessionPresent()) {
            clearSavedSessionButton.setOnPreferenceClickListener(preference -> {
                FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                        AnalyticsParamValue.CLEAR_SAVED_SESSION);
                DevSessionRestorer.clearSession();
                return true;
            });
        } else {
            getPreferenceScreen().removePreference(clearSavedSessionButton);
        }


        Preference forceSubmitButton = findPreference(FORCE_LOG_SUBMIT);
        forceSubmitButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.FORCE_LOG_SUBMISSION);
            CommCareUtil.triggerLogSubmission(getActivity(), false);
            return true;
        });

        Preference recoveryModeButton = findPreference(RECOVERY_MODE);
        recoveryModeButton.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.RECOVERY_MODE);
            startRecoveryMode();
            return true;
        });

        Preference clearAppReleaseTimePref = findPreference(DISABLE_PRE_UPDATE_SYNC);
        clearAppReleaseTimePref.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.DISABLE_PRE_UPDATE_SYNC);
            HiddenPreferences.enableBypassPreUpdateSync(true);
            Toast.makeText(getActivity(), R.string.success, Toast.LENGTH_SHORT).show();
            return true;
        });

        Preference rateLimitPopup = findPreference(ENABLE_RATE_LIMIT_POPUP);
        rateLimitPopup.setOnPreferenceClickListener(preference -> {
            FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                    AnalyticsParamValue.ENABLE_RATE_LIMIT_POPUP);
            HiddenPreferences.disableRateLimitPopup(false);
            Toast.makeText(getActivity(), R.string.success, Toast.LENGTH_SHORT).show();
            return true;
        });

        findPreference(ENABLE_MANUAL_FORM_QUARANTINE)
                .setOnPreferenceClickListener(preference -> {
                    FirebaseAnalyticsUtil.reportAdvancedActionSelected(
                            AnalyticsParamValue.ENABLE_MANUAL_FORM_QUARANTINE);
                    setManualFormQuarantine(true);
                    Toast.makeText(getActivity(), R.string.success, Toast.LENGTH_SHORT).show();
                    return true;
                });
    }

    private void startReportActivity() {
        Intent i = new Intent(getActivity(), ReportProblemActivity.class);
        startActivityForResult(i, REPORT_PROBLEM_ACTIVITY);
    }

    private void startValidationActivity() {
        Intent i = new Intent(getActivity(), CommCareVerificationActivity.class);
        i.putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_SETTINGS, true);
        startActivityForResult(i, VALIDATE_MEDIA_ACTIVITY);
    }

    private void startWifiDirect() {
        Intent i = new Intent(getActivity(), CommCareWiFiDirectActivity.class);
        startActivityForResult(i, WIFI_DIRECT_ACTIVITY);
    }

    private void startFormDump() {
        Intent i = new Intent(getActivity(), CommCareFormDumpActivity.class);
        i.putExtra(CommCareFormDumpActivity.EXTRA_FILE_DESTINATION, CommCareApplication.instance().getCurrentApp().storageRoot());
        startActivityForResult(i, DUMP_FORMS_ACTIVITY);
    }

    private void startRecoveryMode() {
        Intent i = new Intent(getActivity(), RecoveryActivity.class);
        this.startActivity(i);
    }

    private boolean hasP2p() {
        return getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
    }

    private void startConnectionTest() {
        Intent i = new Intent(getActivity(), ConnectionDiagnosticActivity.class);
        startActivity(i);
    }

    public static void clearUserData(final AppCompatActivity activity) {
        int numUnsentAndIncompleteForms = StorageUtils.getNumUnsentAndIncompleteForms();
        StandardAlertDialog d =
                new StandardAlertDialog(activity,
                        Localization.get("clear.user.data.warning.title"),
                        getClearUserDataMessage(numUnsentAndIncompleteForms));
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            if (which == AlertDialog.BUTTON_POSITIVE) {
                AppUtils.clearUserData();
                activity.setResult(RESULT_DATA_RESET);
                activity.finish();
            }
            dialog.dismiss();
        };
        d.setPositiveButton(
                StringUtils.getStringRobust(activity,
                        getClearUserDataPositiveOption(numUnsentAndIncompleteForms)),
                listener);
        d.setNegativeButton(StringUtils.getStringRobust(activity, R.string.cancel), listener);
        d.showNonPersistentDialog();
    }

    private static String getClearUserDataMessage(int numUnsentAndIncompleteForms) {
        if (numUnsentAndIncompleteForms > 0)
            return Localization.get("clear.user.data.warning.message.delete.forms", String.valueOf(numUnsentAndIncompleteForms));
        else
            return Localization.get("clear.user.data.warning.message");
    }

    private static int getClearUserDataPositiveOption(int numUnsentAndIncompleteForms) {
        if (numUnsentAndIncompleteForms > 0)
            return R.string.clear_user_data_delete_form;
        else
            return R.string.ok;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REPORT_PROBLEM_ACTIVITY || requestCode == VALIDATE_MEDIA_ACTIVITY) {
            getActivity().finish();
        } else if (requestCode == WIFI_DIRECT_ACTIVITY || requestCode == DUMP_FORMS_ACTIVITY) {
            if (resultCode == RESULT_CANCELED) {
                return;
            }

            String messageKey = getBulkFormMessageKey(resultCode);
            if (messageKey == null) {
                return;
            }

            int dumpedCount = intent.getIntExtra(KEY_NUMBER_DUMPED, -1);
            Intent returnIntent = new Intent();
            returnIntent.putExtra(FORM_PROCESS_COUNT_KEY, dumpedCount);
            returnIntent.putExtra(FORM_PROCESS_MESSAGE_KEY, messageKey);
            getActivity().setResult(RESULT_FORMS_PROCESSED, returnIntent);
            getActivity().finish();
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

    public static boolean isManualFormQuarantineAllowed() {
        return DeveloperPreferences.doesPropertyMatch(ENABLE_MANUAL_FORM_QUARANTINE, PrefValues.NO, PrefValues.YES);
    }

    public static void setManualFormQuarantine(boolean enabled) {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putString(ENABLE_MANUAL_FORM_QUARANTINE, enabled ? PrefValues.YES : PrefValues.NO)
                .apply();
    }
}

