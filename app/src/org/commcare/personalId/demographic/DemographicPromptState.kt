package org.commcare.personalId.demographic

import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.personalId.PersonalIdUserPreferences
import kotlin.time.Duration.Companion.hours

enum class BannerStage {
    PLAIN,
    WARNING,
    FINAL,
}

class ProfileCompletion(
    val filledFieldCount: Int,
    val totalFieldCount: Int,
) {
    val isComplete: Boolean
        get() = filledFieldCount == totalFieldCount
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

    fun profileCompletion(): ProfileCompletion {
        val fields =
            with(ConnectUserDatabaseUtil.getUser()) {
                listOf(email, gender, dateOfBirth, languages, education, chwExperience, techProficiency)
            }
        return ProfileCompletion(fields.count { !it.isNullOrBlank() }, fields.size)
    }

    fun shouldShow(completion: ProfileCompletion): Boolean = !completion.isComplete && currentTimeMillis() >= nextShowAt

    fun stage(): BannerStage =
        when {
            promptCount >= FINAL_STAGE_PROMPT_COUNT -> BannerStage.FINAL
            promptCount >= WARNING_STAGE_PROMPT_COUNT -> BannerStage.WARNING
            else -> BannerStage.PLAIN
        }

    fun recordDecline() = recordResponse()

    fun recordCompleteTapped() = recordResponse()

    private fun recordResponse() {
        PersonalIdUserPreferences.setDemographicPromptCount(promptCount + 1)
        PersonalIdUserPreferences.setDemographicPromptNextShowAt(currentTimeMillis() + SNOOZE_MILLIS)
    }

    companion object {
        private const val WARNING_STAGE_PROMPT_COUNT = 2
        private const val FINAL_STAGE_PROMPT_COUNT = 3
        private val SNOOZE_MILLIS = 24.hours.inWholeMilliseconds
    }
}
