package org.commcare.activities.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.android.model.PersonalIdValidAndCorruptCredential
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.utils.MultipleAppsUtil
import org.commcare.utils.parseIsoDateForSorting

class PersonalIdCredentialViewModel(application: Application) : AndroidViewModel(application) {
    private val _apiError =
        MutableLiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>>()
    val apiError: LiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>> = _apiError

    private val _earnedCredentials = MutableLiveData<List<PersonalIdCredential>>()
    val earnedCredentials: LiveData<List<PersonalIdCredential>> = _earnedCredentials

    private val _pendingCredentials = MutableLiveData<List<PersonalIdCredential>>()
    val pendingCredentials: LiveData<List<PersonalIdCredential>> = _pendingCredentials

    private val _installedAppRecords = MutableLiveData<List<PersonalIdCredential>>()

    private val user = ConnectUserDatabaseUtil.getUser(application)
    val userName: String = user.name
    val profilePhoto: String? = user.photo

    init {
        _installedAppRecords.value = initInstalledAppsList()
    }

    fun retrieveAndProcessCredentials() {
        object : PersonalIdApiHandler<PersonalIdValidAndCorruptCredential>() {
            override fun onSuccess(result: PersonalIdValidAndCorruptCredential) {
                val earned = result.validCredentials
                val earnedAppIds = earned.map { it.appId }.toSet()
                val installedApps = _installedAppRecords.value.orEmpty()

                val pending = installedApps.filter { it.appId !in earnedAppIds }

                _earnedCredentials.postValue(
                    earned.sortedByDescending { parseIsoDateForSorting(it.issuedDate) }
                )
                _pendingCredentials.postValue(
                    pending.sortedByDescending { parseIsoDateForSorting(it.issuedDate) }
                )
            }


            override fun onFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?
            ) {
                _apiError.postValue(failureCode to t)
            }
        }.retrieveCredentials(getApplication(), userName, user.password)
    }

    private fun initInstalledAppsList(): List<PersonalIdCredential> {
        return MultipleAppsUtil.getUsableAppRecords().map { record ->
            PersonalIdCredential().apply {
                appId = record.applicationId
                title = record.displayName ?: ""
            }
        }
    }
}

