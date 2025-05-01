package org.commcare.activities;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import org.commcare.CommCareApplication;
import org.commcare.core.services.CommCarePreferenceManagerFactory;
import org.commcare.dalvik.R;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.PrefValues;
import org.commcare.views.dialogs.StandardAlertDialog;

import java.util.Date;

import static org.commcare.core.network.CommCareNetworkServiceGenerator.CURRENT_DRIFT;
import static org.commcare.core.network.CommCareNetworkServiceGenerator.MAX_DRIFT_SINCE_LAST_HEARTBEAT;
import static org.javarosa.core.model.utils.DateUtils.DAY_IN_MS;

public class DriftHelper {

    private static final String INCORRECT_TIME_WARNING_ENABLED = "incorrect_time_warning_enabled";
    private static final String LAST_INCORRECT_TIME_WARNING_AT = "last_incorrect_time_warning_at";

    static boolean shouldShowDriftWarning() {
        if (isWarningEnabled()) {
            long lastIncorrrectTimeWarningAt = getPreferences().getLong(LAST_INCORRECT_TIME_WARNING_AT, -1);
            if (new Date().getTime() - lastIncorrrectTimeWarningAt > DAY_IN_MS) {
                return true;
            }
        }
        return false;
    }

    static void updateLastDriftWarningTime() {
        getPreferences().edit().putLong(LAST_INCORRECT_TIME_WARNING_AT, new Date().getTime()).apply();
    }

    public static long getCurrentDrift() {
        return getPreferences().getLong(CURRENT_DRIFT, 0);
    }

    static StandardAlertDialog getDriftDialog(Context context) {
        StandardAlertDialog driftDialog = StandardAlertDialog.getBasicAlertDialog(
                context.getResources().getString(R.string.incorrect_time_dialog_title),
                context.getResources().getString(R.string.incorrect_time_dialog_message),
                null);
        driftDialog.setPositiveButton(context.getResources().getString(R.string.incorrect_time_dialog_correct_time), (dialog, which) -> {
            SettingsHelper.launchDateSettings(context);
            dialog.dismiss();
        });
        driftDialog.setNegativeButton(context.getResources().getString(R.string.incorrect_time_dialog_cancel), ((dialog, which) -> dialog.dismiss()));
        return driftDialog;
    }

    private static boolean isWarningEnabled() {
        return DeveloperPreferences.doesPropertyMatch(INCORRECT_TIME_WARNING_ENABLED, PrefValues.NO, PrefValues.YES);
    }

    private static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance());
    }

    public static long getMaxDriftSinceLastHeartbeat() {
        return CommCarePreferenceManagerFactory.getCommCarePreferenceManager().getLong(MAX_DRIFT_SINCE_LAST_HEARTBEAT, 0);
    }

    public static void clearMaxDriftSinceLastHeartbeat() {
        CommCarePreferenceManagerFactory.getCommCarePreferenceManager().putLong(MAX_DRIFT_SINCE_LAST_HEARTBEAT, 0);
    }
}
