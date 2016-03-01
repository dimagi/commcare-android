package org.commcare.utils;

import android.content.SharedPreferences;
import android.text.Spannable;
import android.text.format.DateUtils;
import android.widget.TextView;

import org.commcare.activities.CommCareHomeActivity;
import org.commcare.adapters.HomeCardDisplayData;
import org.commcare.adapters.SquareButtonViewHolder;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.models.FormRecord;
import org.javarosa.core.services.locale.Localization;

import java.text.DateFormat;
import java.util.Date;

/**
 * Logic that populates the sync button's notification text
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SyncDetailCalculations {
    private final static String UNSENT_FORM_NUMBER_KEY = "unsent-number-limit";
    private final static String UNSENT_FORM_TIME_KEY = "unsent-time-limit";

    public static void updateSubText(final CommCareHomeActivity activity,
                                     SquareButtonViewHolder squareButtonViewHolder,
                                     HomeCardDisplayData cardDisplayData) {
        SqlStorage<FormRecord> formsStorage = CommCareApplication._().getUserStorage(FormRecord.class);
        int numUnsentForms = formsStorage.getIDsForValue(FormRecord.META_STATUS, FormRecord.STATUS_UNSENT).size();
        if (numUnsentForms > 0) {
            Spannable syncIndicator = (activity.localize("home.sync.indicator",
                    new String[]{String.valueOf(numUnsentForms), cardDisplayData.text}));
            squareButtonViewHolder.subTextView.setText(syncIndicator);
            squareButtonViewHolder.subTextView.setTextColor(activity.getResources().getColor(cardDisplayData.subTextColor));
        } else {
            showSyncMessage(squareButtonViewHolder.subTextView,
                    activity.getResources().getColor(cardDisplayData.subTextColor),
                    activity.getResources().getColor(R.color.cc_attention_negative_text),
                    numUnsentForms);
        }
    }

    private static void showSyncMessage(TextView subTextView, int normalColor, int warningColor, int numUnsentForms) {
        CharSequence syncTimeMessage;
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        long lastSyncTime = prefs.getLong("last-succesful-sync", 0);
        if (lastSyncTime == 0) {
            syncTimeMessage = Localization.get("home.sync.message.last.never");
        } else {
            syncTimeMessage = DateUtils.formatSameDayTime(lastSyncTime, new Date().getTime(), DateFormat.DEFAULT, DateFormat.DEFAULT);
        }

        String message = "";
        if (numUnsentForms == 1) {
            message += Localization.get("home.sync.message.unsent.singular") + "\n";
        } else if (numUnsentForms > 1) {
            message += Localization.get("home.sync.message.unsent.plural", new String[]{String.valueOf(numUnsentForms)}) + "\n";
        }

        message += Localization.get("home.sync.message.last", new String[]{syncTimeMessage.toString()});
        subTextView.setText(message);
        if (isSyncStronglyNeeded(numUnsentForms, lastSyncTime)) {
            subTextView.setTextColor(warningColor);
        } else {
            subTextView.setTextColor(normalColor);
        }
    }

    private static boolean isSyncStronglyNeeded(int numUnsentForms, long lastSyncTime) {
        return unsentFormNumberLimitExceeded(numUnsentForms) ||
                unsentFormTimeLimitExceeded(lastSyncTime);
    }

    private static boolean unsentFormNumberLimitExceeded(int numUnsentForms) {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        int unsentFormNumberLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_NUMBER_KEY, "5"));
        return numUnsentForms > unsentFormNumberLimit;
    }

    private static boolean unsentFormTimeLimitExceeded(long lastSyncTime) {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        int unsentFormTimeLimit = Integer.parseInt(prefs.getString(UNSENT_FORM_TIME_KEY, "5"));

        long now = new Date().getTime();
        int secs_ago = (int)((lastSyncTime - now) / 1000);
        int days_ago = secs_ago / 86400;

        return ((-days_ago) > unsentFormTimeLimit) &&
                prefs.getString("server-tether", "push-only").equals("sync");
    }
}
