package org.commcare.personalId.profile.demographic

import androidx.annotation.StringRes
import org.commcare.dalvik.R

/** A dropdown choice on the demographic profile: [key] is stored and sent, [labelRes] is shown. */
interface DemographicOption {
    val key: String

    @get:StringRes
    val labelRes: Int
}

enum class Gender(
    override val key: String,
    override val labelRes: Int,
) : DemographicOption {
    FEMALE("female", R.string.personalid_demographic_gender_female),
    MALE("male", R.string.personalid_demographic_gender_male),
    PREFER_NOT_TO_SAY("prefer_not_to_say", R.string.personalid_demographic_gender_prefer_not_to_say),
}

enum class Education(
    override val key: String,
    override val labelRes: Int,
) : DemographicOption {
    NO_FORMAL_EDUCATION("no_formal_education", R.string.personalid_demographic_education_none),
    HIGH_SCHOOL("high_school", R.string.personalid_demographic_education_high_school),
    COLLEGE_DIPLOMA("college_diploma", R.string.personalid_demographic_education_college_diploma),
    BACHELORS_DEGREE("bachelors_degree", R.string.personalid_demographic_education_bachelors),
    MASTERS_OR_HIGHER("masters_or_higher", R.string.personalid_demographic_education_masters),
}

enum class ChwExperience(
    override val key: String,
    override val labelRes: Int,
) : DemographicOption {
    NONE("no_experience", R.string.personalid_demographic_chw_experience_none),
    ONE_TO_TWO_YEARS("1_2_years", R.string.personalid_demographic_chw_experience_1_2),
    THREE_TO_FIVE_YEARS("3_5_years", R.string.personalid_demographic_chw_experience_3_5),
    SIX_PLUS_YEARS("6_plus_years", R.string.personalid_demographic_chw_experience_6_plus),
}

enum class TechProficiency(
    override val key: String,
    override val labelRes: Int,
) : DemographicOption {
    NONE("no_experience", R.string.personalid_demographic_tech_proficiency_none),
    BEGINNER("beginner", R.string.personalid_demographic_tech_proficiency_beginner),
    COMFORTABLE("comfortable", R.string.personalid_demographic_tech_proficiency_comfortable),
    ADVANCED("advanced", R.string.personalid_demographic_tech_proficiency_advanced),
    EXPERT("expert", R.string.personalid_demographic_tech_proficiency_expert),
}

/** The option stored as [key], or null when unset or written by a version with options this one doesn't know. */
inline fun <reified T> demographicOptionFromKey(key: String?): T? where T : Enum<T>, T : DemographicOption =
    enumValues<T>().firstOrNull { it.key == key }
