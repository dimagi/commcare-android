package org.commcare.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object PushNotificationApiHelper {


    suspend fun retrieveLatestPushNotifications(context: Context): Result<List<PushNotificationRecord>>{
        val pushNotificationListResult = callPushNotificationApi(context)
        return pushNotificationListResult
    }

    suspend fun callPushNotificationApi(context: Context): Result<List<PushNotificationRecord>>{

        val user = ConnectUserDatabaseUtil.getUser(context)
        return suspendCoroutine { continuation ->

            object : PersonalIdApiHandler<List<PushNotificationRecord>>() {
                override fun onSuccess(result: List<PushNotificationRecord>) {
                    CoroutineScope(Dispatchers.IO).launch {
                        updatePushNotifications(context,result)
                    }
                    continuation.resume(Result.success(result))
                }

                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?
                ) {
                    continuation.resume(Result.failure(Exception(PersonalIdApiErrorHandler.handle(context, failureCode, t))))
                }
            }.retrieveNotifications(context, user.userId, user.password)
        }
    }


    suspend fun updatePushNotifications(context: Context, pushNotificationList: List<PushNotificationRecord>): Boolean {

        val savedNotificationIds = NotificationRecordDatabaseHelper.storeNotifications(context, pushNotificationList)
        val user = ConnectUserDatabaseUtil.getUser(context)
        return suspendCoroutine { continuation ->
            object : PersonalIdApiHandler<Unit>() {
                override fun onSuccess(result: Unit) {
                    NotificationRecordDatabaseHelper.updateColumnForNotifications(
                        context,
                        savedNotificationIds
                    ) { record ->
                        record.acknowledged = true
                    }
                    continuation.resumeWith(Result.success(true))
                }

                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?
                ) {
                    continuation.resumeWith(Result.success(false))
                }
            }.updateNotifications(context, user.userId, user.password, savedNotificationIds)
        }
    }


}