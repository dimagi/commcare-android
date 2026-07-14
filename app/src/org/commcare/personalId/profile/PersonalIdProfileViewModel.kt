package org.commcare.personalId.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.commcare.connect.database.ConnectUserDatabaseUtil

class PersonalIdProfileViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _profileDisplayModel = MutableLiveData<PersonalIdProfileDisplayModel>()
    val profileDisplayModel: LiveData<PersonalIdProfileDisplayModel> = _profileDisplayModel

    fun loadProfile() {
        val user = ConnectUserDatabaseUtil.getUser(getApplication())
        _profileDisplayModel.value = PersonalIdProfileDisplayModel.fromUserRecord(getApplication(), user)
    }
}
