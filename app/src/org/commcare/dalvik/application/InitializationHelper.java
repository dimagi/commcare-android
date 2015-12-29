package org.commcare.dalvik.application;

import android.app.Activity;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.DispatchActivity;
import org.javarosa.core.services.locale.Localization;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class InitializationHelper {
    public static boolean isDbInBadState(DispatchActivity activity) {
        int dbState = CommCareApplication._().getDatabaseState();
        if (dbState == CommCareApplication.STATE_MIGRATION_FAILED) {
            CommCareApplication._().triggerHandledAppExit(activity,
                    activity.getString(R.string.migration_definite_failure),
                    activity.getString(R.string.migration_failure_title), false);
            return true;
        } else if (dbState == CommCareApplication.STATE_MIGRATION_QUESTIONABLE) {
            CommCareApplication._().triggerHandledAppExit(activity,
                    activity.getString(R.string.migration_possible_failure),
                    activity.getString(R.string.migration_failure_title), false);
            return true;
        } else if (dbState == CommCareApplication.STATE_CORRUPTED) {
            handleDamagedApp(activity);
            return true;
        }
        return false;
    }

    public static void handleDamagedApp(DispatchActivity activity) {
        if (!CommCareApplication._().isStorageAvailable()) {
            createNoStorageDialog(activity);
        } else {
            // See if we're logged in. If so, prompt for recovery.
            try {
                CommCareApplication._().getSession();
                activity.showDialog(DispatchActivity.DIALOG_CORRUPTED);
            } catch (SessionUnavailableException e) {
                // Otherwise, log in first
                activity.launchLogin();
            }
        }
    }

    private static void createNoStorageDialog(Activity activity) {
        CommCareApplication._().triggerHandledAppExit(activity,
                Localization.get("app.storage.missing.message"),
                Localization.get("app.storage.missing.title"));
    }
}
