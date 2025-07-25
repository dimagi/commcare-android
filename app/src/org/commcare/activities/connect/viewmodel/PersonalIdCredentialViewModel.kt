package org.commcare.activities.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.android.database.connect.models.PersonalIdValidAndCorruptCredential
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.utils.parseIsoDateForSorting

class PersonalIdCredentialViewModel(application: Application) : AndroidViewModel(application) {

    private val _credentialsLiveData = MutableLiveData<PersonalIdValidAndCorruptCredential>()
    val credentialsLiveData: LiveData<PersonalIdValidAndCorruptCredential> = _credentialsLiveData

    private val _apiError =
        MutableLiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>>()
    val apiError: LiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>> =
        _apiError

    private val _earnedCredentials = MutableLiveData<List<PersonalIdCredential>>()
    val earnedCredentials: LiveData<List<PersonalIdCredential>> = _earnedCredentials

    private val _pendingCredentials = MutableLiveData<List<PersonalIdCredential>>()
    val pendingCredentials: LiveData<List<PersonalIdCredential>> = _pendingCredentials

    fun retrieveCredentials() {
        val user = ConnectUserDatabaseUtil.getUser(getApplication())

        object : PersonalIdApiHandler<PersonalIdValidAndCorruptCredential>() {
            override fun onSuccess(result: PersonalIdValidAndCorruptCredential) {
                _credentialsLiveData.postValue(result)
            }

            override fun onFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?
            ) {
                _apiError.postValue(failureCode to t)
            }
        }.retrieveCredentials(getApplication(), user.name, user.password)
    }

    fun setFilteredCredentialLists(
        earned: List<PersonalIdCredential>,
        pending: List<PersonalIdCredential>
    ) {
        val sortedEarned = earned.sortedByDescending { parseIsoDateForSorting(it.issuedDate) }
        val sortedPending = pending.sortedByDescending { parseIsoDateForSorting(it.issuedDate) }

        _earnedCredentials.value = sortedEarned
        _pendingCredentials.value = sortedPending
    }
}

