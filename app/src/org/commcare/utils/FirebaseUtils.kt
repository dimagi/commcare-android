package org.commcare.utils

import org.commcare.dalvik.BuildConfig
import org.commcare.preferences.MainConfigurablePreferences

object FirebaseUtils {
    @JvmStatic
    val isFirebaseEnabled: Boolean
        /**
         * Returns whether Firebase services are available and configured. Firebase is disabled in debug mode
         * When false, all Firebase API calls must be skipped.
         */
        get() = !BuildConfig.DEBUG && BuildConfig.FIREBASE_ENABLED

    @JvmStatic
    val isCrashlyticsEnabled: Boolean
        get() = isFirebaseEnabled && BuildConfig.USE_CRASHLYTICS

    @JvmStatic
    val isAnalyticsEnabled: Boolean
        get() = isFirebaseEnabled && MainConfigurablePreferences.isAnalyticsEnabled()
}
