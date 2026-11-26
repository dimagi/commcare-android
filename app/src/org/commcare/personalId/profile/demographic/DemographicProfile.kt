package org.commcare.personalId.profile.demographic

import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.utils.optNonBlankStringSafe
import org.json.JSONObject
import java.io.Serializable

data class DemographicProfile(
    val gender: String? = null,
    val dateOfBirth: String? = null,
    val languages: String? = null,
    val education: String? = null,
    val chwExperience: String? = null,
    val techProficiency: String? = null,
) : Serializable {
    fun toRequestParams(): Map<String, String> =
        mapOf(
            KEY_GENDER to gender.orEmpty(),
            KEY_DATE_OF_BIRTH to dateOfBirth.orEmpty(),
            KEY_LANGUAGES to languages.orEmpty(),
            KEY_EDUCATION to education.orEmpty(),
            KEY_CHW_EXPERIENCE to chwExperience.orEmpty(),
            KEY_TECH_PROFICIENCY to techProficiency.orEmpty(),
        )

    fun applyTo(user: ConnectUserRecord) {
        user.gender = gender
        user.dateOfBirth = dateOfBirth
        user.languages = languages
        user.education = education
        user.chwExperience = chwExperience
        user.techProficiency = techProficiency
    }

    companion object {
        private const val KEY_GENDER = "gender"
        private const val KEY_DATE_OF_BIRTH = "date_of_birth"
        private const val KEY_LANGUAGES = "languages"
        private const val KEY_EDUCATION = "education"
        private const val KEY_CHW_EXPERIENCE = "chw_experience"
        private const val KEY_TECH_PROFICIENCY = "tech_proficiency"

        @JvmStatic
        fun fromJson(json: JSONObject): DemographicProfile =
            DemographicProfile(
                gender = json.optNonBlankStringSafe(KEY_GENDER),
                dateOfBirth = json.optNonBlankStringSafe(KEY_DATE_OF_BIRTH),
                languages = json.optNonBlankStringSafe(KEY_LANGUAGES),
                education = json.optNonBlankStringSafe(KEY_EDUCATION),
                chwExperience = json.optNonBlankStringSafe(KEY_CHW_EXPERIENCE),
                techProficiency = json.optNonBlankStringSafe(KEY_TECH_PROFICIENCY),
            )
    }
}
