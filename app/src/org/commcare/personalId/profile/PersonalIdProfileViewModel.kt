package org.commcare.personalId.profile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.utils.PhoneNumberHelper

class PersonalIdProfileViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _profileDisplayModel = MutableLiveData<PersonalIdProfileDisplayModel>()
    val profileDisplayModel: LiveData<PersonalIdProfileDisplayModel> = _profileDisplayModel

    init {
        val user = ConnectUserDatabaseUtil.getUser(application)
        _profileDisplayModel.value = getProfileDisplayModelForUser(application, user)
    }

    companion object {
        fun getProfileDisplayModelForUser(
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
