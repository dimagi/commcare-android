package org.commcare.utils;

import org.commcare.dalvik.BuildConfig;

public class FirebaseUtils {

    private FirebaseUtils() {}

    /**
     * Returns whether Firebase services are available and configured. Firebase is disabled in debug mode
     * When false, all Firebase API calls must be skipped.
     */
    public static boolean isFirebaseEnabled() {
        return !BuildConfig.DEBUG && BuildConfig.FIREBASE_ENABLED;
    }

    public static boolean isCrashlyticsEnabled() {
        return isFirebaseEnabled() && BuildConfig.USE_CRASHLYTICS;
    }
}