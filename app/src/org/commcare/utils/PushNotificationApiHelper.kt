package org.commcare.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectConstants.NOTIFICATION_BODY
import org.commcare.connect.ConnectConstants.NOTIFICATION_CHANNEL_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_MESSAGE_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_STATUS
import org.commcare.connect.ConnectConstants.NOTIFICATION_TIME_STAMP
import org.commcare.connect.ConnectConstants.NOTIFICATION_TITLE
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectConstants.PAYMENT_ID
import org.commcare.connect.ConnectConstants.REDIRECT_ACTION
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object PushNotificationApiHelper {


    fun retrieveLatestPushNotificationsWithCallback(context: Context, listener: ConnectActivityCompleteListener){
        CoroutineScope(Dispatchers.IO).launch {
            retrieveLatestPushNotifications(context).onSuccess {
                listener.connectActivityComplete(true)
            }.onFailure {
                listener.connectActivityComplete(false)
            }
        }
    }



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
                        if (result.isNotEmpty()){
                            updatePushNotifications(context,result)
                         }
                    }
                    continuation.resume(Result.success(result))
                }

                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?
                ) {
                    continuation.resume(Result.failure(Exception(PersonalIdApiErrorHandler.handle(context, failureCode, t))))
                }
            }.retrieveNotifications(context, user)
        }
    }


    suspend fun updatePushNotifications(context: Context, pushNotificationList: List<PushNotificationRecord>): Boolean {

        val savedNotificationIds = NotificationRecordDatabaseHelper.storeNotifications(context, pushNotificationList)
        val user = ConnectUserDatabaseUtil.getUser(context)
        return suspendCoroutine { continuation ->
            object : PersonalIdApiHandler<Boolean>() {
                override fun onSuccess(result: Boolean) {
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


    fun convertPNRecordsToPayload(pnsRecords:List<PushNotificationRecord>?): ArrayList<Map<String,String>>{
        val pns = ArrayList<Map<String,String>>()
        pnsRecords?.let {
            it.map {pnRecord ->
                pns.add(convertPNRecordToPayload(pnRecord))
            }
        }
        return pns
    }

    fun convertPNRecordToPayload(pnRecord: PushNotificationRecord): HashMap<String,String> {
        val pn = HashMap<String,String>()
        pn.put(REDIRECT_ACTION,pnRecord.action)
        pn.put(NOTIFICATION_TITLE,pnRecord.title)
        pn.put(NOTIFICATION_BODY,pnRecord.body)
        pn.put(NOTIFICATION_ID,""+pnRecord.notificationId)
        pn.put(NOTIFICATION_TIME_STAMP,pnRecord.createdDate.toString())
        pn.put(NOTIFICATION_STATUS,pnRecord.confirmationStatus)
        pn.put(NOTIFICATION_MESSAGE_ID,""+pnRecord.connectMessageId)
        pn.put(NOTIFICATION_CHANNEL_ID,""+pnRecord.channel)
        pn.put(OPPORTUNITY_ID,""+pnRecord.opportunityId)
        pn.put(PAYMENT_ID,""+pnRecord.paymentId)
        return pn
    }


}