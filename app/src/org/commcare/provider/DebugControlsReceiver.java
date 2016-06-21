package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.preferences.DevSessionRestorer;

/**
 * Process broadcasts requesting to
 * - uninstall app
 * - save the current commcare user session.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DebugControlsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.endsWith("SessionCaptureAction")) {
            captureSession();
        } else if (action.endsWith("UninstallApp")) {
            uninstallApp(intent.getStringExtra("app_id"));
        }
    }

    private static void captureSession() {
        DevSessionRestorer.saveSessionToPrefs();
        DevSessionRestorer.enableAutoLogin();
    }

    private static void uninstallApp(String appId) {
        ApplicationRecord appRecord = CommCareApplication._().getAppById(appId);
        if (appRecord != null) {
            CommCareApplication._().expireUserSession();
            CommCareApplication._().uninstall(appRecord);
        }
    }
}
