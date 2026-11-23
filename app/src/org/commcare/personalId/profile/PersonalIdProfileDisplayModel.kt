package org.commcare.personalId.profile

import android.content.Context
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.personalId.demographic.ChwExperience
import org.commcare.personalId.demographic.DateOfBirth
import org.commcare.personalId.demographic.DemographicOption
import org.commcare.personalId.demographic.Education
import org.commcare.personalId.demographic.Gender
import org.commcare.personalId.demographic.PersonalIdLanguages
import org.commcare.personalId.demographic.TechProficiency
import org.commcare.personalId.demographic.demographicOptionFromKey
import org.commcare.utils.PhoneNumberHelper

/** Demographic values are display labels, or null (empty list for [languages]) when not set. */
data class PersonalIdProfileDisplayModel(
    val name: String,
    val displayPhone: String,
    val email: String,
    val photoBase64: String?,
    val gender: String? = null,
    val languages: List<String> = emptyList(),
    val dateOfBirth: String? = null,
    val education: String? = null,
    val chwExperience: String? = null,
    val techProficiency: String? = null,
) {
    companion object {
        fun fromUserRecord(
            context: Context,
            user: ConnectUserRecord,
        ): PersonalIdProfileDisplayModel {
            val displayPhone = PhoneNumberHelper.getInstance(context).formatForDisplay(user.primaryPhone)

            return PersonalIdProfileDisplayModel(
                name = user.name,
                displayPhone = displayPhone,
                email = user.email.orEmpty(),
                photoBase64 = user.photo,
                gender = context.labelOf(demographicOptionFromKey<Gender>(user.gender)),
                languages = PersonalIdLanguages.labelsFor(context, PersonalIdLanguages.parseCodes(user.languages)),
                dateOfBirth = DateOfBirth.forDisplay(user.dateOfBirth),
                education = context.labelOf(demographicOptionFromKey<Education>(user.education)),
                chwExperience = context.labelOf(demographicOptionFromKey<ChwExperience>(user.chwExperience)),
                techProficiency = context.labelOf(demographicOptionFromKey<TechProficiency>(user.techProficiency)),
            )
        }

        private fun Context.labelOf(option: DemographicOption?): String? = option?.let { getString(it.labelRes) }
    }
}
