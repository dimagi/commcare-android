package org.commcare.preferences;

import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.core.util.Pair;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.dalvik.BuildConfig;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.models.database.AndroidSandbox;
import org.commcare.modern.session.SessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.util.CommCarePlatform;
import org.commcare.utils.SessionStateUninitException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Logic to save password and user's session and restore them during auto-login
 * when dev option is enabled
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class DevSessionRestorer {

    private static final String TAG = DevSessionRestorer.class.getSimpleName();

    public final static String CURRENT_SESSION = "current_user_session";
    public final static String CURRENT_FORM_ENTRY_SESSION = "current_form_entry_session";

    /**
     * @return Username and password of last login; null if auto-login not
     * enabled
     */
    public static Pair<String, String> getAutoLoginCreds(boolean force) {
        if (force || autoLoginEnabled()) {
            SharedPreferences prefs =
                    CommCareApplication.instance().getCurrentApp().getAppPreferences();
            String lastUser =
                    prefs.getString(HiddenPreferences.LAST_LOGGED_IN_USER, "");
            String lastPass =
                    prefs.getString(HiddenPreferences.LAST_PASSWORD, "");

            if (!"".equals(lastPass)) {
                return new Pair<>(lastUser, lastPass);
            }
        }

        return null;
    }

    public static void storeAutoLoginCreds(String username, String password) {
        tryAutoLoginPasswordSave(password, true);
        SharedPreferences prefs =
                CommCareApplication.instance().getCurrentApp().getAppPreferences();
        prefs.edit().putString(HiddenPreferences.LAST_LOGGED_IN_USER, username).apply();
    }

    /**
     * Save password into app preferences if auto-login is enabled
     */
    public static void tryAutoLoginPasswordSave(String password, boolean force) {
        if (force || autoLoginEnabled()) {
            SharedPreferences prefs =
                    CommCareApplication.instance().getCurrentApp().getAppPreferences();
            prefs.edit().putString(HiddenPreferences.LAST_PASSWORD, password).apply();
        }
    }

    private static boolean autoLoginEnabled() {
        return BuildConfig.DEBUG && DeveloperPreferences.isAutoLoginEnabled();
    }

    public static void enableAutoLogin() {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putString(DeveloperPreferences.ENABLE_AUTO_LOGIN, PrefValues.YES)
                .apply();
    }

    public static void enableSessionSaving() {
        CommCareApplication.instance().getCurrentApp().getAppPreferences()
                .edit()
                .putString(DeveloperPreferences.ENABLE_SAVE_SESSION, PrefValues.YES)
                .apply();
    }

    public static void clearPassword(SharedPreferences prefs) {
        prefs.edit().remove(HiddenPreferences.LAST_PASSWORD).apply();
    }

    /**
     * Builds a session object using serialized data stored in the app
     * preferences. Currently doesn't support restoring the session stack, only
     * the current frame.
     */
    public static AndroidSessionWrapper restoreSessionFromPrefs(CommCarePlatform platform) {
        SharedPreferences prefs =
                CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String serializedSession = prefs.getString(DevSessionRestorer.CURRENT_SESSION, null);
        if (serializedSession != null) {
            try {
                byte[] sessionBytes = Base64.decode(serializedSession, Base64.DEFAULT);
                DataInputStream stream =
                        new DataInputStream(new ByteArrayInputStream(sessionBytes));

                CommCareSession restoredSession =
                        CommCareSession.restoreSessionFromStream(platform, stream);

                Log.i(TAG, "Restoring session from storage");
                return new AndroidSessionWrapper(new SessionWrapper(restoredSession, platform,
                        new AndroidSandbox(CommCareApplication.instance())));
            } catch (Exception e) {
                clearSession(prefs);
                Log.w(TAG, "Restoring session from serialized file failed");
            }
        }

        // no saved session or failed to restore; return a blank session
        return new AndroidSessionWrapper(platform);
    }

    /**
     * Save the current session frame to app shared preferences.
     */
    public static void saveSessionToPrefs() {
        CommCareApp ccApp = CommCareApplication.instance().getCurrentApp();
        if (ccApp == null) {
            return;
        }

        String serializedSession = getSerializedSessionString();
        String formEntrySession = FormEntryActivity.getFormEntrySessionString();

        ccApp.getAppPreferences().edit()
                .putString(DevSessionRestorer.CURRENT_SESSION, serializedSession)
                .putString(DevSessionRestorer.CURRENT_FORM_ENTRY_SESSION, formEntrySession)
                .apply();
    }

    public static String getSerializedSessionString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream serializedStream = new DataOutputStream(baos);

        try {
            CommCareApplication.instance().getCurrentSession().serializeSessionState(serializedStream);
        } catch (IOException e) {
            Log.w(TAG, "Failed to serialize session");
            return "";
        } catch (SessionStateUninitException e) {
            Log.w(TAG, "Attempting to save a non-existent session");
            return "";
        }

        String serializedSession = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        try {
            serializedStream.close();
        } catch (IOException e) {
            Log.d(TAG, "Failed to close session serialization stream.");
        }

        return serializedSession;
    }

    public static boolean savedSessionPresent() {
        SharedPreferences prefs =
                CommCareApplication.instance().getCurrentApp().getAppPreferences();
        String serializedSession = prefs.getString(DevSessionRestorer.CURRENT_SESSION, null);
        return serializedSession != null;
    }

    public static void clearSession() {
        clearSession(CommCareApplication.instance().getCurrentApp().getAppPreferences());
    }

    private static void clearSession(SharedPreferences prefs) {
        prefs.edit()
                .remove(DevSessionRestorer.CURRENT_SESSION)
                .remove(DevSessionRestorer.CURRENT_FORM_ENTRY_SESSION)
                .apply();
    }
}
