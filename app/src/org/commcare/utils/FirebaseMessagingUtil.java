package org.commcare.utils;

import android.content.SharedPreferences;
import android.util.Base64;

import androidx.preference.PreferenceManager;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.messaging.FirebaseMessaging;

import org.commcare.CommCareApplication;
import org.commcare.dalvik.BuildConfig;
import org.commcare.services.FCMMessageData;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

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

    public static String serializeFCMMessageData(FCMMessageData fcmMessageData){
        byte[] serializedMessageData = SerializationUtil.serialize(fcmMessageData);
        String base64EncodedMessageData = Base64.encodeToString(serializedMessageData, Base64.DEFAULT);
        return base64EncodedMessageData;
    }

    public static FCMMessageData deserializeFCMMessageData(String base64EncodedSerializedFCMMessageData){
        if (base64EncodedSerializedFCMMessageData != null) {
            byte [] serializedMessageData = Base64.decode(base64EncodedSerializedFCMMessageData, Base64.DEFAULT);
            return SerializationUtil.deserialize(serializedMessageData, FCMMessageData.class);
        }
        return null;
    }
}
