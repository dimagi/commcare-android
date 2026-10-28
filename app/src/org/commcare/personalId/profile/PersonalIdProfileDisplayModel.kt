package org.commcare.personalId.profile

import android.content.Context
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.utils.PhoneNumberHelper

data class PersonalIdProfileDisplayModel(
    val name: String,
    val displayPhone: String,
    val email: String,
    val photoBase64: String?,
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
            )
        }
    }
}
