package org.commcare.dalvik.application;

import android.app.Activity;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.R;
import org.commcare.dalvik.activities.CommCareHomeActivity;
import org.javarosa.core.services.locale.Localization;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class InitializationHelper {
    public static void checkDbState(Activity activity) {
        int dbState = CommCareApplication._().getDatabaseState();
        if (dbState == CommCareApplication.STATE_MIGRATION_FAILED) {
            CommCareApplication._().triggerHandledAppExit(activity,
                    activity.getString(R.string.migration_definite_failure),
                    activity.getString(R.string.migration_failure_title), false);
        } else if (dbState == CommCareApplication.STATE_MIGRATION_QUESTIONABLE) {
            CommCareApplication._().triggerHandledAppExit(activity,
                    activity.getString(R.string.migration_possible_failure),
                    activity.getString(R.string.migration_failure_title), false);
        } else if (dbState == CommCareApplication.STATE_CORRUPTED) {
            handleDamagedApp(activity);
        }
    }

    public static void handleDamagedApp(Activity activity) {
        if (!CommCareApplication._().isStorageAvailable()) {
            createNoStorageDialog(activity);
        } else {
            // See if we're logged in. If so, prompt for recovery.
            try {
                CommCareApplication._().getSession();
                // TODO PLM, work with dispatch activity
                activity.showDialog(CommCareHomeActivity.DIALOG_CORRUPTED);
            } catch(SessionUnavailableException e) {
                // Otherwise, log in first
                // TODO PLM
                //launchLogin();
            }
        }
    }

    private static void createNoStorageDialog(Activity activity) {
        CommCareApplication._().triggerHandledAppExit(activity,
                Localization.get("app.storage.missing.message"),
                Localization.get("app.storage.missing.title"));
    }

}
