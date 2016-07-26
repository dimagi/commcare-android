package org.commcare.provider;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.LoginActivity;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.database.global.models.ApplicationRecord;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.DevSessionRestorer;
import org.joda.time.DateTime;

import java.util.Date;

/**
 * Process broadcasts requesting to
 * - uninstall app
 * - save the current commcare user session.
 * - log into the currently seated app
 * - invalidate sync token to force recovery on sync
 * - invalidate user key record, future login will hit HQ for a new UKR
 * - set a flag that will include a param to clear the cache on the next restore request
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
        } else if (action.endsWith("ExpireUserKeyRecord")) {
            invalidateUserKeyRecord(intent.getStringExtra("username"));
        } else if (action.endsWith("ClearCacheOnRestore")) {
            CommCareApplication._().setInvalidateCacheFlag(true);
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

    private static void storeFakeCaseDbHash() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        prefs.edit().putString(FAKE_CASE_DB_HASH, "FAKE").apply();
    }

    private static void invalidateUserKeyRecord(String username) {
        CommCareApp app = CommCareApplication._().getCurrentApp();
        SqlStorage<UserKeyRecord> storage = app.getStorage(UserKeyRecord.class);
        UserKeyRecord invalidUkr = null;
        Date yesterday = DateTime.now().minusDays(1).toDate();
        for (UserKeyRecord ukr : storage.getRecordsForValue(UserKeyRecord.META_USERNAME, username)) {
            if (ukr.isActive() && ukr.isCurrentlyValid()) {
                invalidUkr = new UserKeyRecord(
                        ukr.getUsername(), ukr.getPasswordHash(),
                        ukr.getEncryptedKey(), ukr.getWrappedPassword(),
                        ukr.getValidFrom(), yesterday, ukr.getUuid(),
                        ukr.getType());
                break;
            }
        }
        if (invalidUkr != null) {
            storage.write(invalidUkr);
        }
    }

    public static String getFakeCaseDbHash() {
        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
        String fakeHash = prefs.getString(FAKE_CASE_DB_HASH, null);
        prefs.edit().remove(FAKE_CASE_DB_HASH).apply();
        return fakeHash;
    }
}
