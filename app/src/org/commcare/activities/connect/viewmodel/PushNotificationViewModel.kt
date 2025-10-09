package org.commcare.activities.connect.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler

class PushNotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val _fetchApiError =
        MutableLiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>>()
    val fetchApiError: LiveData<Pair<BaseApiHandler.PersonalIdOrConnectApiErrorCodes, Throwable?>> =
        _fetchApiError

    private val _allNotifications = MutableLiveData<List<PushNotificationRecord>>()
    val allNotifications: LiveData<List<PushNotificationRecord>> = _allNotifications

    private val notificationRecordDbHelper = NotificationRecordDatabaseHelper()
    private val user = ConnectUserDatabaseUtil.getUser(application)

    fun loadNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            // Load from DB first
            val cachedNotifications =
                notificationRecordDbHelper.getAllNotifications(getApplication()).orEmpty()
            _allNotifications.postValue(cachedNotifications)

            // Fetch from server
            object : PersonalIdApiHandler<List<PushNotificationRecord>>() {
                override fun onSuccess(result: List<PushNotificationRecord>) {
                    // append new results to UI
                    val currentNotifications = _allNotifications.value.orEmpty()
                    val updatedNotifications = (result + currentNotifications).distinctBy { it.notificationId }
                    _allNotifications.postValue(updatedNotifications)
                    // Save API result into DB and get notification IDs for stored notifications
                    val savedNotificationIds =
                        notificationRecordDbHelper.storeNotifications(
                            getApplication(), result
                        )
                    /*if (savedNotificationIds.isNotEmpty()) {
                        updateNotifications(
                            user.userId,
                            user.password,
                            savedNotificationIds
                        )
                    }*/
                }

                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?
                ) {
                    _fetchApiError.postValue(failureCode to t)
                }
            }.retrieveNotifications(getApplication(), user.userId, user.password)
        }
    }

    /**
     * Update notifications for a list of notification IDs
     */
    fun updateNotifications(userId: String, password: String, savedNotificationIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            object : PersonalIdApiHandler<Unit>() {
                override fun onSuccess(result: Unit) {
                    notificationRecordDbHelper.updateColumnForNotifications(
                        getApplication(),
                        savedNotificationIds
                    ) { record ->
                        record.acknowledged = true
                    }
                }

                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?
                ) {
                    _fetchApiError.postValue(failureCode to t)
                }
            }.updateNotifications(getApplication(), userId, password, savedNotificationIds)
        }
    }
}