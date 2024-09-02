package org.commcare.utils;

import android.content.SharedPreferences;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.widget.TextView;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.adapters.HomeCardDisplayData;
import org.commcare.adapters.SquareButtonViewHolder;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import java.text.DateFormat;
import java.util.Date;

/**
 * Logic that populates the sync button's notification text
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SyncDetailCalculations {
    private static final String UNSENT_FORM_NUMBER_KEY = "unsent-number-limit";
    private static final String UNSENT_FORM_TIME_KEY = "unsent-time-limit";
    private static final String LAST_SYNC_KEY_BASE = "last-succesful-sync-";

    public static void updateSubText(
            final StandardHomeActivity activity,
            SquareButtonViewHolder squareButtonViewHolder,
            HomeCardDisplayData cardDisplayData,
            String notificationText) {

        int numUnsentForms = getNumUnsentForms();
        Pair<Long, String> lastSyncTimeAndMessage = getLastSyncTimeAndMessage();

        Spannable syncIndicator =
                (activity.localize(
                        "home.unsent.forms.indicator",
                        new String[] {String.valueOf(numUnsentForms)}));

        String syncStatus = "";

        if (notificationText != null) {
            syncStatus = notificationText;
        } else if (numUnsentForms == 0) {
            syncStatus = lastSyncTimeAndMessage.second;
        }

        if (numUnsentForms != 0 || HiddenPreferences.shouldShowUnsentFormsWhenZero()) {
            if (!TextUtils.isEmpty(syncStatus)) {
                syncStatus += "\n\n";
            }
            syncStatus += syncIndicator;
        }

        squareButtonViewHolder.subTextView.setText(syncStatus);

        setSyncSubtextColor(
                squareButtonViewHolder.subTextView,
                numUnsentForms,
                lastSyncTimeAndMessage.first,
                activity.getResources().getColor(cardDisplayData.subTextColor),
                activity.getResources().getColor(R.color.cc_dark_warm_accent_color));
    }

    public static int getNumUnsentForms() {
        SqlStorage<FormRecord> formsStorage =
                CommCareApplication.instance().getUserStorage(FormRecord.class);
        try {
            return StorageUtils.getUnsentRecordsForCurrentApp(formsStorage).length;
        } catch (SessionUnavailableException e) {
            // Addresses unexpected issue where this db lookup occurs after session ends.
            // If possible, replace this with fix that addresses root issue
            return 0;
        }
    }

    public static Pair<Long, String> getLastSyncTimeAndMessage() {
        CharSequence syncTimeMessage;
        long lastSyncTime = getLastSyncTime();
        if (lastSyncTime == 0) {
            syncTimeMessage = Localization.get("home.sync.message.last.never");
        } else {
            syncTimeMessage =
                    DateUtils.formatSameDayTime(
                            lastSyncTime,
                            new Date().getTime(),
                            DateFormat.DEFAULT,
                            DateFormat.DEFAULT);
        }
        return new Pair<>(
                lastSyncTime,
                Localization.get(
                        "home.sync.message.last", new String[] {syncTimeMessage.toString()}));
    }

    /**
     * Calculates the number of days synce the user last synced
     *
     * @return the difference between the current date and the date of the last sync. -1 if the user
     *     hasn't ever synced or if the last date of sync is unavailable
     */
    public static int getDaysSinceLastSync() {
        try {
            long lastSync = getLastSyncTime();
            if (lastSync == 0) {
                return -1;
            }
            return getDaysBetweenJavaDatetimes(new Date(lastSync), new Date());
        } catch (Exception e) {
            e.printStackTrace();
            Logger.log(
                    LogTypes.SOFT_ASSERT,
                    "Error Generating Days since last sync: " + e.getMessage());
            return -1;
        }
    }

    public static int getDaysBetweenJavaDatetimes(Date anchor, Date delta) {
        return Days.daysBetween(new LocalDate(anchor), new LocalDate(delta)).getDays();
    }

    public static long getLastSyncTime() {
        return getLastSyncTime(AppUtils.getLoggedInUserName());
    }

    public static long getLastSyncTime(String username) {
        SharedPreferences prefs =
                CommCareApplication.instance().getCurrentApp().getAppPreferences();
        return prefs.getLong(getLastSyncKey(username), 0);
    }

    public static String getLastSyncKey(String username) {
        return LAST_SYNC_KEY_BASE + username;
    }

    private static void setSyncSubtextColor(
            TextView subtext,
            int numUnsentForms,
            long lastSyncTime,
            int normalColor,
            int warningColor) {
        if (isSyncStronglyNeeded(numUnsentForms, lastSyncTime)) {
            subtext.setTextColor(warningColor);
        } else {
            subtext.setTextColor(normalColor);
        }
    }

    private static boolean isSyncStronglyNeeded(int numUnsentForms, long lastSyncTime) {
        return unsentFormNumberLimitExceeded(numUnsentForms)
                || unsentFormTimeLimitExceeded(lastSyncTime);
    }

    private static boolean unsentFormNumberLimitExceeded(int numUnsentForms) {
        SharedPreferences prefs =
                CommCareApplication.instance().getCurrentApp().getAppPreferences();
        int unsentFormNumberLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_NUMBER_KEY, "5"));
        return numUnsentForms > unsentFormNumberLimit;
    }

    private static boolean unsentFormTimeLimitExceeded(long lastSyncTime) {
        SharedPreferences prefs =
                CommCareApplication.instance().getCurrentApp().getAppPreferences();
        double unsentFormTimeLimitInDays =
                Double.parseDouble(prefs.getString(UNSENT_FORM_TIME_KEY, "5"));
        long unsentFormTimeLimitInMsecs = (int) (unsentFormTimeLimitInDays * 24 * 60 * 60 * 1000);

        long now = new Date().getTime();
        long msecsSinceLastSync = (now - lastSyncTime);

        return msecsSinceLastSync > unsentFormTimeLimitInMsecs;
    }
}
