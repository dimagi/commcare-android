package org.commcare.activities.connect.viewmodel

import android.app.Application
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
    var savedNotificationIds: List<String> = emptyList()

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
                    val updatedNotifications = currentNotifications + result
                    _allNotifications.postValue(updatedNotifications.distinctBy { it.notificationId })
                    // Save API result into DB and get notification IDs for stored notifications
                    savedNotificationIds =
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

                }
            }.retrieveNotifications(getApplication(), user.userId, user.password)
        }
    }

    /**
     * Update notifications for a list of notification IDs
     */
    fun updateNotifications(userId: String, password: String, notificationIds: List<String>) {
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

                }
            }.updateNotifications(getApplication(), userId, password, notificationIds)
        }
    }
}