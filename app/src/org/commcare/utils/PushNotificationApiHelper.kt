package org.commcare.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import org.commcare.pn.helper.NotificationBroadcastHelper
import org.commcare.pn.workers.MessagingChannelsKeySyncWorker
import org.commcare.preferences.NotificationPrefs
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object PushNotificationApiHelper {
    const val MESSAGING_CHANNEL_KEYS_SYNC = "MESSAGING_CHANNEL_KEYS_SYNC"
    const val SYNC_BACKOFF_DELAY_IN_MINS: Long = 3

    const val NOTIFICATION_TYPE_MESSAGING = "MESSAGING"

    fun retrieveLatestPushNotificationsWithCallback(
        context: Context,
        listener: ConnectActivityCompleteListener,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            retrieveLatestPushNotifications(context)
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        //  switching to main to touch views
                        listener.connectActivityComplete(true)
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        //  switching to main to touch views
                        listener.connectActivityComplete(false)
                    }
                }
        }
    }

    suspend fun retrieveLatestPushNotifications(context: Context): Result<List<PushNotificationRecord>> {
        val pushNotificationListResult = callPushNotificationApi(context)
        return pushNotificationListResult
    }

    suspend fun callPushNotificationApi(context: Context): Result<List<PushNotificationRecord>> {
        val user = ConnectUserDatabaseUtil.getUser(context)
        return suspendCoroutine { continuation ->

            object : PersonalIdApiHandler<List<PushNotificationRecord>>() {
                override fun onSuccess(result: List<PushNotificationRecord>) {
                    scheduleMessagingChannelsKeySync(context)
                    var newResultWithoutMessaging: List<PushNotificationRecord> = ArrayList()
                    CoroutineScope(Dispatchers.IO).launch {
                        if (result.isNotEmpty()) {
                            val messagingNotiIds = getAllMessagingNotiIds(result) //  required to acknowledge server
                            newResultWithoutMessaging = excludeMessagingFromList(result) // store only without messaging
                            if (newResultWithoutMessaging.isNotEmpty()) {
                                NotificationPrefs.setNotificationAsUnread(context)
                            }
                            NotificationBroadcastHelper.sendNewNotificationBroadcast(context) // broadcast for any
                            val savedNotificationIds =
                                NotificationRecordDatabaseHelper.storeNotifications(
                                    context,
                                    newResultWithoutMessaging, // store only notification without messaging Id
                                )
                            acknowledgeNotificationsReceipt(context, savedNotificationIds + messagingNotiIds) // acknowledge all
                        }
                    }
                    continuation.resume(Result.success(newResultWithoutMessaging))
                }

                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?,
                ) {
                    continuation.resume(
                        Result.failure(
                            Exception(
                                PersonalIdApiErrorHandler.handle(
                                    context,
                                    failureCode,
                                    t,
                                ),
                            ),
                        ),
                    )
                }
            }.retrieveNotifications(context, user)
        }
    }

    suspend fun acknowledgeNotificationsReceipt(
        context: Context,
        savedNotificationIds: List<String>,
    ): Boolean {
        val user = ConnectUserDatabaseUtil.getUser(context)
        return suspendCoroutine { continuation ->
            object : PersonalIdApiHandler<Boolean>() {
                override fun onSuccess(result: Boolean) {
                    NotificationRecordDatabaseHelper.updateColumnForNotifications(
                        context,
                        savedNotificationIds,
                    ) { record ->
                        record.acknowledged = true
                    }
                    continuation.resumeWith(Result.success(true))
                }

                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?,
                ) {
                    continuation.resumeWith(Result.success(false))
                }
            }.updateNotifications(context, user.userId, user.password, savedNotificationIds)
        }
    }

    fun convertPNRecordsToPayload(pnsRecords: List<PushNotificationRecord>?): ArrayList<Map<String, String>> {
        val pns = ArrayList<Map<String, String>>()
        pnsRecords?.let {
            it.map { pnRecord ->
                pns.add(convertPNRecordToPayload(pnRecord))
            }
        }
        return pns
    }

    fun convertPNRecordToPayload(pnRecord: PushNotificationRecord): HashMap<String, String> {
        val pn = HashMap<String, String>()
        pn.put(REDIRECT_ACTION, pnRecord.action)
        pn.put(NOTIFICATION_TITLE, pnRecord.title)
        pn.put(NOTIFICATION_BODY, pnRecord.body)
        pn.put(NOTIFICATION_ID, "" + pnRecord.notificationId)
        pn.put(NOTIFICATION_TIME_STAMP, pnRecord.createdDate.toString())
        pn.put(NOTIFICATION_STATUS, pnRecord.confirmationStatus)
        pn.put(NOTIFICATION_MESSAGE_ID, "" + pnRecord.connectMessageId)
        pn.put(NOTIFICATION_CHANNEL_ID, "" + pnRecord.channel)
        pn.put(OPPORTUNITY_ID, "" + pnRecord.opportunityId)
        pn.put(PAYMENT_ID, "" + pnRecord.paymentId)
        return pn
    }

    fun scheduleMessagingChannelsKeySync(context: Context) {
        val channelsKeySyncWorkRequest =
            OneTimeWorkRequest
                .Builder(MessagingChannelsKeySyncWorker::class.java)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                ).setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    SYNC_BACKOFF_DELAY_IN_MINS,
                    TimeUnit.MINUTES,
                ).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MESSAGING_CHANNEL_KEYS_SYNC,
            ExistingWorkPolicy.KEEP,
            channelsKeySyncWorkRequest,
        )
    }

    private fun getAllMessagingNotiIds(notificationsList: List<PushNotificationRecord>): List<String> =
        notificationsList
            .filter {
                NOTIFICATION_TYPE_MESSAGING.equals(it.notificationType)
            }.map {
                it.notificationId
            }

    private fun excludeMessagingFromList(notificationsList: List<PushNotificationRecord>) =
        notificationsList.filter {
            !NOTIFICATION_TYPE_MESSAGING.equals(it.notificationType)
        }
}
