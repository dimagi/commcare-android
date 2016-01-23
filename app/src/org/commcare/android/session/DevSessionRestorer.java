package org.commcare.android.session;

import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.util.SessionStateUninitException;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.preferences.CommCarePreferences;
import org.commcare.dalvik.preferences.DeveloperPreferences;
import org.commcare.session.CommCareSession;
import org.commcare.util.CommCarePlatform;

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

    /**
     * @return Username and password of last login; null if auto-login not
     * enabled
     */
    public static Pair<String, String> getAutoLoginCreds() {
        if (autoLoginEnabled()) {
            SharedPreferences prefs =
                    CommCareApplication._().getCurrentApp().getAppPreferences();
            String lastUser =
                    prefs.getString(CommCarePreferences.LAST_LOGGED_IN_USER, "");
            String lastPass =
                    prefs.getString(CommCarePreferences.LAST_PASSWORD, "");

            if (!"".equals(lastPass)) {
                return new Pair<>(lastUser, lastPass);
            }
        }

        return null;
    }

    /**
     * Save password into app preferences if auto-login is enabled
     */
    public static void tryAutoLoginPasswordSave(String password) {
        if (autoLoginEnabled()) {
            SharedPreferences prefs =
                    CommCareApplication._().getCurrentApp().getAppPreferences();
            prefs.edit().putString(CommCarePreferences.LAST_PASSWORD, password).commit();
        }
    }

    private static boolean autoLoginEnabled() {
        return BuildConfig.DEBUG && DeveloperPreferences.isAutoLoginEnabled();
    }

    public static void clearPassword(SharedPreferences prefs) {
        prefs.edit().remove(CommCarePreferences.LAST_PASSWORD).commit();
    }

    /**
     * Builds a session object using serialized data stored in the app
     * preferences. Currently doesn't support restoring the session stack, only
     * the current frame.
     */
    public static AndroidSessionWrapper restoreSessionFromPrefs(CommCarePlatform platform) {
        SharedPreferences prefs =
                CommCareApplication._().getCurrentApp().getAppPreferences();
        String serializedSession = prefs.getString(CommCarePreferences.CURRENT_SESSION, null);
        if (serializedSession != null) {
            try {
                byte[] sessionBytes = Base64.decode(serializedSession, Base64.DEFAULT);
                DataInputStream stream =
                        new DataInputStream(new ByteArrayInputStream(sessionBytes));

                CommCareSession restoredSession =
                        CommCareSession.restoreSessionFromStream(platform, stream);

                Log.i(TAG, "Restoring session from storage");
                return new AndroidSessionWrapper(restoredSession);
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
        CommCareApp ccApp = CommCareApplication._().getCurrentApp();
        if (ccApp == null) {
            return;
        }

        SharedPreferences prefs = ccApp.getAppPreferences();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream serializedStream = new DataOutputStream(baos);
        try {
            CommCareApplication._().getCurrentSession().serializeSessionState(serializedStream);
        } catch (IOException e) {
            Log.w(TAG, "Failed to serialize session");
            return;
        } catch (SessionStateUninitException e) {
            Log.w(TAG, "Attempting to save a non-existent session");
            return;
        }
        String serializedSession = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        prefs.edit().putString(CommCarePreferences.CURRENT_SESSION, serializedSession).commit();
        try {
            serializedStream.close();
        } catch (IOException e) {
            Log.d(TAG, "Failed to close session serialization stream.");
        }
    }

    public static boolean savedSessionPresent() {
        SharedPreferences prefs =
                CommCareApplication._().getCurrentApp().getAppPreferences();
        String serializedSession = prefs.getString(CommCarePreferences.CURRENT_SESSION, null);
        return serializedSession != null;
    }

    public static void clearSession() {
        clearSession(CommCareApplication._().getCurrentApp().getAppPreferences());
    }

    private static void clearSession(SharedPreferences prefs) {
        prefs.edit().remove(CommCarePreferences.CURRENT_SESSION).commit();
    }
}
