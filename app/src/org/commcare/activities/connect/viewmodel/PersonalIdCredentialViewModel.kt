package org.commcare.activities.connect.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.commcare.android.database.connect.models.PersonalIdValidAndCorruptCredential
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler

class PersonalIdCredentialViewModel : ViewModel() {

    private val _credentialsLiveData = MutableLiveData<PersonalIdValidAndCorruptCredential>()
    val credentialsLiveData: LiveData<PersonalIdValidAndCorruptCredential> = _credentialsLiveData

    private val _apiError = MutableLiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>>()
    val apiError: LiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>> = _apiError

    fun retrieveCredentials(context: Context) {
        val user = ConnectUserDatabaseUtil.getUser(context)

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
        }.retrieveCredentials(context, user.name, user.password)
    }
}
