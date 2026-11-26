package org.commcare.personalId.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.fragments.personalId.EmailHelper
import org.commcare.personalId.profile.demographic.DemographicProfile
import org.commcare.personalId.profile.demographic.PersonalIdLanguages
import org.commcare.utils.CommCareAttemptCounter

/**
 * Holds the in-progress Edit Profile form state in a [SavedStateHandle] so typed values
 * survive rotation, process death, and the round-trip to email verification. The persisted
 * [ConnectUserRecord] stays the single source of truth for the original field values.
 */
class PersonalIdProfileEditViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    var user: ConnectUserRecord = ConnectUserDatabaseUtil.getUser()
        private set
    val emailOtpTracker = CommCareAttemptCounter()

    val currentName: String
        get() = savedStateHandle[KEY_CURRENT_NAME] ?: user.name

    val currentEmail: String
        get() = savedStateHandle[KEY_CURRENT_EMAIL] ?: user.email.orEmpty()

    /** Demographic fields as stored: option keys, an ISO date, comma-separated language codes; null when unset. */
    val currentGender: String? get() = demographic(KEY_GENDER) { gender }
    val currentDateOfBirth: String? get() = demographic(KEY_DATE_OF_BIRTH) { dateOfBirth }
    val currentLanguages: String? get() = demographic(KEY_LANGUAGES) { languages }
    val currentEducation: String? get() = demographic(KEY_EDUCATION) { education }
    val currentChwExperience: String? get() = demographic(KEY_CHW_EXPERIENCE) { chwExperience }
    val currentTechProficiency: String? get() = demographic(KEY_TECH_PROFICIENCY) { techProficiency }

    val currentDemographics: DemographicProfile
        get() =
            DemographicProfile(
                gender = currentGender,
                dateOfBirth = currentDateOfBirth,
                languages = currentLanguages,
                education = currentEducation,
                chwExperience = currentChwExperience,
                techProficiency = currentTechProficiency,
            )

    fun onNameChanged(name: String) {
        savedStateHandle[KEY_CURRENT_NAME] = name.trim()
    }

    fun onEmailChanged(email: String) {
        savedStateHandle[KEY_CURRENT_EMAIL] = email.trim()
    }

    fun onGenderChanged(key: String?) = setDemographic(KEY_GENDER, key)

    fun onDateOfBirthChanged(date: String?) = setDemographic(KEY_DATE_OF_BIRTH, date)

    fun onLanguagesChanged(codes: String?) = setDemographic(KEY_LANGUAGES, codes)

    fun onEducationChanged(key: String?) = setDemographic(KEY_EDUCATION, key)

    fun onChwExperienceChanged(key: String?) = setDemographic(KEY_CHW_EXPERIENCE, key)

    fun onTechProficiencyChanged(key: String?) = setDemographic(KEY_TECH_PROFICIENCY, key)

    fun onPhotoUpdated(photoBase64: String) {
        user.photo = photoBase64
    }

    fun isNameModified(): Boolean = currentName != user.name

    fun isEmailModified(): Boolean = currentEmail != user.email.orEmpty()

    fun isDemographicsModified(): Boolean =
        currentGender != user.gender ||
            currentDateOfBirth != user.dateOfBirth ||
            PersonalIdLanguages.parseCodes(currentLanguages).toSet() != PersonalIdLanguages.parseCodes(user.languages).toSet() ||
            currentEducation != user.education ||
            currentChwExperience != user.chwExperience ||
            currentTechProficiency != user.techProficiency

    fun isProfileDetailsModified(): Boolean = isNameModified() || isDemographicsModified()

    fun isModified(): Boolean = isProfileDetailsModified() || isEmailModified()

    fun isNameValid(): Boolean = currentName.isNotBlank()

    fun isEmailValid(): Boolean =
        if (currentEmail.isEmpty()) {
            // Clearing an existing email is not allowed.
            user.email.isNullOrEmpty()
        } else {
            EmailHelper.isValidEmail(currentEmail)
        }

    fun isEmailEmpty(): Boolean = currentEmail.isEmpty()

    /** Empty demographic fields never block saving; only the name and email are validated. */
    fun canSave(): Boolean = isModified() && isNameValid() && isEmailValid()

    /**
     * Applies the edited profile details to a freshly-read record so fields committed by other
     * flows since this ViewModel was created (e.g. a photo update) are not clobbered.
     */
    fun commitProfileDetails() {
        val demographics = currentDemographics
        commit {
            name = currentName
            demographics.applyTo(this)
        }
    }

    private fun commit(update: ConnectUserRecord.() -> Unit) {
        val storedUser = ConnectUserDatabaseUtil.getUser()
        storedUser.update()
        ConnectUserDatabaseUtil.storeUser(storedUser)
        user = storedUser
    }

    private fun demographic(
        key: String,
        stored: ConnectUserRecord.() -> String?,
    ): String? = if (savedStateHandle.contains(key)) savedStateHandle[key] else user.stored()

    private fun setDemographic(
        key: String,
        value: String?,
    ) {
        savedStateHandle[key] = value?.ifEmpty { null }
    }

    companion object {
        private const val KEY_CURRENT_NAME = "current_name"
        private const val KEY_CURRENT_EMAIL = "current_email"
        private const val KEY_GENDER = "current_gender"
        private const val KEY_DATE_OF_BIRTH = "current_date_of_birth"
        private const val KEY_LANGUAGES = "current_languages"
        private const val KEY_EDUCATION = "current_education"
        private const val KEY_CHW_EXPERIENCE = "current_chw_experience"
        private const val KEY_TECH_PROFICIENCY = "current_tech_proficiency"
    }
}
