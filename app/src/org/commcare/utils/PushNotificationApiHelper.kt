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
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.connect.network.connectId.parser.NotificationParseResult
import org.commcare.pn.helper.NotificationBroadcastHelper
import org.commcare.pn.workers.MessagingChannelsKeySyncWorker
import org.commcare.preferences.NotificationPrefs
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object PushNotificationApiHelper {
    const val MESSAGING_CHANNEL_KEYS_SYNC = "MESSAGING_CHANNEL_KEYS_SYNC"
    const val SYNC_BACKOFF_DELAY_IN_MINS: Long = 3

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

    private suspend fun callPushNotificationApi(context: Context): Result<List<PushNotificationRecord>> {
        val user = ConnectUserDatabaseUtil.getUser(context)
        return suspendCoroutine { continuation ->

            object : PersonalIdApiHandler<NotificationParseResult>() {
                override fun onSuccess(parseResult: NotificationParseResult) {
                    scheduleMessagingChannelsKeySync(context)
                    CoroutineScope(Dispatchers.IO).launch {
                        val (savedNotifications, savedNotificationIds) = processParsedDataIntoDB(context, parseResult)

                        // Update notification preferences and send broadcasts
                        if (savedNotificationIds.isNotEmpty()) {
                            NotificationPrefs.setNotificationAsUnread(context)
                        }
                        if (savedNotificationIds.isNotEmpty() || parseResult.messagingNotificationIds.isNotEmpty()) {
                            NotificationBroadcastHelper.sendNewNotificationBroadcast(context)
                        }

                        // Acknowledge all notifications (both stored and messaging)
                        acknowledgeNotificationsReceipt(context, savedNotificationIds + parseResult.messagingNotificationIds)

                        continuation.resume(Result.success(savedNotifications))
                    }
                }

                override fun onFailure(
                    failureCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?,
                ) {
                    continuation.resume(
                        Result.failure(
                            Exception(
                                PersonalIdOrConnectApiErrorHandler.handle(
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

    /**
     * Processes parsed notification data into the database
     * @param context Android context
     * @param parseResult Result from parsing notification response
     * @return Pair of (saved notification records, saved notification IDs)
     */
    private fun processParsedDataIntoDB(
        context: Context,
        parseResult: NotificationParseResult,
    ): Pair<List<PushNotificationRecord>, List<String>> {
        // Store messaging channels
        if (parseResult.channels.isNotEmpty()) {
            ConnectMessagingDatabaseHelper.storeMessagingChannels(context, parseResult.channels, true)
        }

        // Store messaging messages
        if (parseResult.messages.isNotEmpty()) {
            ConnectMessagingDatabaseHelper.storeMessagingMessages(context, parseResult.messages, false)
        }

        // Store non-messaging notifications
        val savedNotificationIds =
            if (parseResult.nonMessagingNotifications.isNotEmpty()) {
                NotificationRecordDatabaseHelper.storeNotifications(context, parseResult.nonMessagingNotifications)
            } else {
                emptyList()
            }

        val savedNotifications =
            parseResult.nonMessagingNotifications.filter {
                savedNotificationIds.contains(it.notificationId)
            }

        return Pair(savedNotifications, savedNotificationIds)
    }

    private suspend fun acknowledgeNotificationsReceipt(
        context: Context,
        savedNotificationIds: List<String>,
    ): Boolean {
        //  don't call server unnecessarily if nothing to update
        if(savedNotificationIds.isEmpty()){
            return true
        }
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

    private fun scheduleMessagingChannelsKeySync(context: Context) {
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
}
