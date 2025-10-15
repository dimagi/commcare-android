package org.commcare.activities.connect.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.utils.PushNotificationApiHelper.retrieveLatestPushNotifications
class PushNotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val _fetchApiError =
        MutableLiveData<String>()
    val fetchApiError: LiveData<String> =
        _fetchApiError

    private val _allNotifications = MutableLiveData<List<PushNotificationRecord>>()
    val allNotifications: LiveData<List<PushNotificationRecord>> = _allNotifications

    private val user = ConnectUserDatabaseUtil.getUser(application)

    fun loadNotifications(isRefreshed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            // Load from DB first
            if (!isRefreshed) {
                val cachedNotifications =
                    NotificationRecordDatabaseHelper.getAllNotifications(getApplication()).orEmpty()
                _allNotifications.postValue(cachedNotifications)
            }

            val latestPushNotificationsFromApi = retrieveLatestPushNotifications(application)
            latestPushNotificationsFromApi.onSuccess {
                val currentNotifications = _allNotifications.value.orEmpty()
                val updatedNotifications = (it + currentNotifications).distinctBy { it.notificationId }
                _allNotifications.postValue(updatedNotifications)
            }.onFailure {
                _fetchApiError.postValue(it.message)
            }

        }
    }
}