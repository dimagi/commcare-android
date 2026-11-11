package org.commcare.personalId

import androidx.annotation.StringDef

/**
 * Utility class to check for enabled feature flags related to PersonalID functionality.
 */
class PersonalIdFeatureFlagChecker {
    @StringDef
    @Retention(AnnotationRetention.SOURCE)
    annotation class FeatureFlag {
        companion object {
            const val WORK_HISTORY_PENDING_TAB = "work_history_pending_tab"
            const val DEMOGRAPHIC_PROMPT = "demographic_prompt"
        }
    }

    companion object {
        @JvmStatic
        fun isFeatureEnabled(
            @FeatureFlag feature: String,
        ): Boolean =
            when (feature) {
                FeatureFlag.WORK_HISTORY_PENDING_TAB -> false
                FeatureFlag.DEMOGRAPHIC_PROMPT -> false
                else -> throw IllegalStateException("Unknown feature flag: $feature")
            }
    }
}
