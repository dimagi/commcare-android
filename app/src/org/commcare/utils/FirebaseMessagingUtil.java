package org.commcare.utils;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.commcare.CommCareApplication;

public class FirebaseMessagingUtil {
    public static final String FCM_TOKEN = "fcm_token";
    public static final String FCM_TOKEN_TIME = "fcm_token_time";

    public static String getFCMToken() {
        return PreferenceManager
                .getDefaultSharedPreferences(CommCareApplication.instance())
                .getString(FCM_TOKEN, null);
    }

    public static long getFCMTokenTime() {
        return PreferenceManager
                .getDefaultSharedPreferences(CommCareApplication.instance())
                .getLong(FCM_TOKEN_TIME, 0);
    }

    public static void updateFCMToken(String newToken) {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(CommCareApplication.instance());
        sharedPreferences.edit().putString(FCM_TOKEN, newToken).apply();
        sharedPreferences.edit().putLong(FCM_TOKEN_TIME,System.currentTimeMillis()).apply();
    }
}
