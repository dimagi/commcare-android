package org.commcare.utils;

import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.messaging.FirebaseMessaging;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.BuildConfig;
import org.javarosa.core.services.Logger;

public class FirebaseMessagingUtil {
    public static final String FCM_TOKEN = "fcm_token";
    public static final String FCM_TOKEN_TIME = "fcm_token_time";

    /**
     * The domain name in the application profile file comes in the <domain>.commcarehq.org form,
     * this is standard across the different HQ servers. This constant is to store that suffix and
     * be used to remove it form the user domain name to match how the domain represented in the backend
     */
    public static final String USER_DOMAIN_SERVER_URL_SUFFIX = ".commcarehq.org";

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

    public static String removeServerUrlFromUserDomain(String userDomain) {
        if (userDomain == null){
            return null;
        }

        if (userDomain.contains(USER_DOMAIN_SERVER_URL_SUFFIX)){
            return userDomain.replace(USER_DOMAIN_SERVER_URL_SUFFIX, "");
        }
        return userDomain;
    }

    public static void verifyToken() {
        // TODO: Enable FCM in debug mode
        if(!BuildConfig.DEBUG){
            // Retrieve the current Firebase Cloud Messaging (FCM) registration token
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(handleFCMTokenRetrieval());
        }
    }

    private static OnCompleteListener handleFCMTokenRetrieval(){
        return (OnCompleteListener<String>) task -> {
            if (!task.isSuccessful()) {
                Logger.exception("Fetching FCM registration token failed", task.getException());
            }
        };
    }
}
