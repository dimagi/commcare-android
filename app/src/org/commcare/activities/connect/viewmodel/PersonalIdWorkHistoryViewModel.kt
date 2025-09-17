package org.commcare.activities.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.CommCareApp
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.android.database.global.models.ApplicationRecord
import org.commcare.connect.ConnectDateUtils.parseIsoDateForSorting
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.utils.MultipleAppsUtil
import java.util.ArrayList

class PersonalIdCredentialViewModel(application: Application) : AndroidViewModel(application) {
    private val _apiError =
        MutableLiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>>()
    val apiError: LiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>> = _apiError

    private val _earnedCredentials = MutableLiveData<List<PersonalIdWorkHistory>>()
    val earnedCredentials: LiveData<List<PersonalIdWorkHistory>> = _earnedCredentials

    private val _pendingCredentials = MutableLiveData<List<PersonalIdWorkHistory>>()
    val pendingCredentials: LiveData<List<PersonalIdWorkHistory>> = _pendingCredentials

    private lateinit var installedAppsCredentials : List<PersonalIdWorkHistory>

    private val user = ConnectUserDatabaseUtil.getUser(application)
    val userName: String = user.name
    val profilePhoto: String? = user.photo

    fun retrieveAndProcessCredentials() {
        viewModelScope.launch(Dispatchers.IO) {
            object : PersonalIdApiHandler<List<PersonalIdWorkHistory>>() {
                override fun onSuccess(result: List<PersonalIdWorkHistory>) {
                    val earned = result
                    _earnedCredentials.postValue(
                        earned.sortedByDescending { parseIsoDateForSorting(it.issuedDate) }
                    )

                    if (!::installedAppsCredentials.isInitialized) {
                        installedAppsCredentials = evalInstalledAppsCredentials()
                    }
                    val pending = installedAppsCredentials.filter { installedCredential ->
                        !earned.any { earnedCredential ->
                            earnedCredential.appId == installedCredential.appId &&
                            earnedCredential.title == installedCredential.title &&
                            earnedCredential.level == installedCredential.level
                        }
                    }

                    _pendingCredentials.postValue(pending)
                }


                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?
                ) {
                    _apiError.postValue(failureCode to t)
                }
            }.retrieveCredentials(getApplication(), user.userId, user.password)
        }
    }

    private fun evalInstalledAppsCredentials(): List<PersonalIdWorkHistory> {
        val previousSandbox = CommCareApp.currentSandbox
        val records = MultipleAppsUtil.getUsableAppRecords()
        return try {
            getCredentialsFromAppRecords(records)
        } finally {
            CommCareApp.currentSandbox = previousSandbox
        }
    }

    private fun getCredentialsFromAppRecords(records: ArrayList<ApplicationRecord>): List<PersonalIdWorkHistory> {
        return records.flatMap { record ->
            val commcareApp = CommCareApp(record)
            commcareApp.setupSandbox()
            val profile = commcareApp.initApplicationProfile();
            profile.credentials.map { credential ->
                PersonalIdWorkHistory().apply {
                    appId = record.applicationId
                    title = record.displayName ?: ""
                    level = credential.level
                }
            }
        }
    }

}

