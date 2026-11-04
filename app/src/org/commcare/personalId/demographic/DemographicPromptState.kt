package org.commcare.personalId.demographic

import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.personalId.PersonalIdUserPreferences
import kotlin.time.Duration.Companion.hours

enum class BannerStage {
    PLAIN,
    WARNING,
    FINAL,
}

/**
 * Decides whether the demographic profile banner shows and at which stage. Completeness covers the six
 * demographic fields plus email, which is optional at sign-up, and is always computed from the user
 * record; only the prompt count and the next-show time are stored.
 */
class DemographicPromptState(
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    val promptCount: Int
        get() = PersonalIdUserPreferences.getDemographicPromptCount()

    val nextShowAt: Long
        get() = PersonalIdUserPreferences.getDemographicPromptNextShowAt()

    fun shouldShow(): Boolean = !isProfileComplete(user()) && currentTimeMillis() >= nextShowAt

    fun stage(): BannerStage =
        when {
            promptCount >= FINAL_STAGE_PROMPT_COUNT -> BannerStage.FINAL
            promptCount >= WARNING_STAGE_PROMPT_COUNT -> BannerStage.WARNING
            else -> BannerStage.PLAIN
        }

    fun filledFieldCount(): Int = countFilledFields(user())

    fun recordDecline() = recordResponse()

    fun recordCompleteTapped() = recordResponse()

    private fun recordResponse() {
        PersonalIdUserPreferences.setDemographicPromptCount(promptCount + 1)
        PersonalIdUserPreferences.setDemographicPromptNextShowAt(currentTimeMillis() + SNOOZE_MILLIS)
    }

    private fun user(): ConnectUserRecord = ConnectUserDatabaseUtil.getUser()

    companion object {
        const val PROFILE_FIELD_COUNT = 7
        private const val WARNING_STAGE_PROMPT_COUNT = 2
        private const val FINAL_STAGE_PROMPT_COUNT = 3
        private val SNOOZE_MILLIS = 24.hours.inWholeMilliseconds

        fun countFilledFields(user: ConnectUserRecord): Int =
            listOf(
                user.email,
                user.gender,
                user.dateOfBirth,
                user.languages,
                user.education,
                user.chwExperience,
                user.techProficiency,
            ).count { !it.isNullOrBlank() }

        fun isProfileComplete(user: ConnectUserRecord): Boolean = countFilledFields(user) == PROFILE_FIELD_COUNT
    }
}
