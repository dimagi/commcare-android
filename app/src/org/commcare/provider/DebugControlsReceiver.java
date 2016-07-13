package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.commcare.CommCareApplication;
import org.commcare.activities.LoginActivity;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.preferences.DevSessionRestorer;

/**
 * Process broadcasts requesting to
 * - uninstall app
 * - save the current commcare user session.
 * - log into the currently seated app
 * - invalidate sync token to force recovery on sync
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DebugControlsReceiver extends BroadcastReceiver {
    private final static String FAKE_CASE_DB_HASH = "fake_case_db_hash";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.endsWith("SessionCaptureAction")) {
            captureSession();
        } else if (action.endsWith("UninstallApp")) {
            uninstallApp(intent.getStringExtra("app_id"));
        } else if (action.endsWith("LoginWithCreds")) {
            login(context, intent.getStringExtra("username"), intent.getStringExtra("password"));
        } else if (action.endsWith("TriggerSyncRecover")) {
            storeFakeCaseDbHash();
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

    private static void login(Context context, String username, String password) {
        DevSessionRestorer.enableAutoLogin();
        DevSessionRestorer.storeAutoLoginCreds(username, password);
        Intent loginIntent = new Intent(context, LoginActivity.class);
        loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(loginIntent);
    }

    public static void storeFakeCaseDbHash() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        prefs.edit().putString(FAKE_CASE_DB_HASH, "FAKE").apply();
    }

    public static String getFakeCaseDbHash() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String fakeHash = prefs.getString(FAKE_CASE_DB_HASH, null);
        prefs.edit().remove(FAKE_CASE_DB_HASH).apply();
        return fakeHash;
    }
}
