package org.commcare.personalId

import androidx.annotation.StringDef

/**
 * Utility class to check for enabled feature flags related to Personal ID functionality.
 */
class PersonalIdFeatureFlagChecker {

    @StringDef
    @Retention(AnnotationRetention.SOURCE)
    annotation class FeatureFlag {
        companion object {
            const val WORK_HISTORY = "work_history"
            const val NOTIFICATIONS = "notifications"
        }
    }

    companion object {

        @JvmStatic
        fun isFeatureEnabled(@FeatureFlag feature: String): Boolean {
            return when(feature) {
                FeatureFlag.WORK_HISTORY -> true
                FeatureFlag.NOTIFICATIONS -> false
                else -> throw IllegalStateException("Unknown feature flag: $feature")
            }
        }
    }
}
