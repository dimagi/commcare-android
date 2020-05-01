package org.commcare.utils;

import android.content.SharedPreferences;
import android.text.format.DateUtils;

import org.commcare.CommCareApplication;
import org.commcare.preferences.PrefValues;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.MainConfigurablePreferences;

import java.util.Calendar;
import java.util.Date;

public class PendingCalcs {


    /**
     * Used to check if an update, sync, or log submission is pending, based upon the last time
     * it occurred and the expected period between occurrences
     */
    public static boolean isPending(long last, long period) {
        long now = new Date().getTime();

        // 1) Straightforward - Time is greater than last + duration
        long diff = now - last;
        if (diff > period) {
            return true;
        }

        // 2) For daily stuff, we want it to be the case that if the last time you synced was the day prior,
        // you still sync, so people can get into the cycle of doing it once in the morning, which
        // is more valuable than syncing mid-day.
        if (isDifferentDayInPast(now, last, period)) {
            return true;
        }

        // 3) Major time change - (Phone might have had its calendar day manipulated).
        // for now we'll simply say that if last was more than a day in the future (timezone blur)
        // we should also trigger
        return (now < (last - DateUtils.DAY_IN_MILLIS));
    }

    private static boolean isDifferentDayInPast(long now, long last, long period) {
        Calendar lastRestoreCalendar = Calendar.getInstance();
        lastRestoreCalendar.setTimeInMillis(last);

        return period == DateUtils.DAY_IN_MILLIS &&
                lastRestoreCalendar.get(Calendar.DAY_OF_WEEK) != Calendar.getInstance().get(Calendar.DAY_OF_WEEK) &&
                now > last;
    }

    /**
     * @return True if there is a sync action pending.
     */
    public static boolean getPendingSyncStatus() {
        SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();

        long period = -1;

        // new flag, read what it is.
        String periodic = prefs.getString(HiddenPreferences.AUTO_SYNC_FREQUENCY, PrefValues.FREQUENCY_NEVER);

        if (!periodic.equals(PrefValues.FREQUENCY_NEVER)) {
            period = DateUtils.DAY_IN_MILLIS * (periodic.equals(PrefValues.FREQUENCY_DAILY) ? 1 : 7);
        } else {
            // Old flag, use a day by default
            if ("true".equals(prefs.getString("cc-auto-update", "false"))) {
                period = DateUtils.DAY_IN_MILLIS;
            }
        }

        // If we didn't find a period, bail
        if (period == -1) {
            return false;
        }

        return PendingCalcs.isPending(HiddenPreferences.getLastUploadSyncAttempt(), period);
    }

}
