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

class PersonalIdWorkHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val _apiError =
        MutableLiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>>()
    val apiError: LiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>> = _apiError

    private val _earnedWorkHistory = MutableLiveData<List<PersonalIdWorkHistory>>()
    val earnedWorkHistory: LiveData<List<PersonalIdWorkHistory>> = _earnedWorkHistory

    private val _pendingWorkHistory = MutableLiveData<List<PersonalIdWorkHistory>>()
    val pendingWorkHistory: LiveData<List<PersonalIdWorkHistory>> = _pendingWorkHistory

    private lateinit var installedAppsWorkHistory : List<PersonalIdWorkHistory>

    private val user = ConnectUserDatabaseUtil.getUser(application)
    val userName: String = user.name
    val profilePhoto: String? = user.photo

    fun retrieveAndProcessWorkHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            object : PersonalIdApiHandler<List<PersonalIdWorkHistory>>() {
                override fun onSuccess(result: List<PersonalIdWorkHistory>) {
                    val earned = result
                    _earnedWorkHistory.postValue(
                        earned.sortedByDescending { parseIsoDateForSorting(it.issuedDate) }
                    )

                    if (!::installedAppsWorkHistory.isInitialized) {
                        installedAppsWorkHistory = evalInstalledAppsWorkHistory()
                    }
                    val pending = installedAppsWorkHistory.filter { installedWorkHistory ->
                        !earned.any { earnedWorkHistory ->
                            earnedWorkHistory.appId == installedWorkHistory.appId &&
                            earnedWorkHistory.title == installedWorkHistory.title &&
                            earnedWorkHistory.level == installedWorkHistory.level
                        }
                    }

                    _pendingWorkHistory.postValue(pending)
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

    private fun evalInstalledAppsWorkHistory(): List<PersonalIdWorkHistory> {
        val previousSandbox = CommCareApp.currentSandbox
        val records = MultipleAppsUtil.getUsableAppRecords()
        return try {
            getWorkHistoryFromAppRecords(records)
        } finally {
            CommCareApp.currentSandbox = previousSandbox
        }
    }

    private fun getWorkHistoryFromAppRecords(records: ArrayList<ApplicationRecord>): List<PersonalIdWorkHistory> {
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

