package org.commcare.utils;

import org.commcare.dalvik.BuildConfig;

public class FirebaseUtils {

    private FirebaseUtils() {}

    /**
     * Returns whether Firebase services are available and configured.
     * When false, all Firebase API calls must be skipped.
     */
    public static boolean isFirebaseEnabled() {
        return BuildConfig.FIREBASE_ENABLED;
    }
}