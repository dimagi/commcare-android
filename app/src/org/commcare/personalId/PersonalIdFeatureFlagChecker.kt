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
        }
    }

    companion object {

        @JvmStatic
        fun isFeatureEnabled(@FeatureFlag feature: String): Boolean {
            return when(feature) {
                FeatureFlag.WORK_HISTORY -> false
                else -> throw IllegalStateException("Unknown feature flag: $feature")
            }
        }
    }
}
