package org.commcare.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_REQUIRE_APP_SYNC
import org.commcare.android.database.connect.models.PushNotificationRecord.Companion.META_SESSION_ENDPOINT_ID
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectConstants.NOTIFICATION_BODY
import org.commcare.connect.ConnectConstants.NOTIFICATION_CHANNEL_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_KEY
import org.commcare.connect.ConnectConstants.NOTIFICATION_MESSAGE_ID
import org.commcare.connect.ConnectConstants.NOTIFICATION_STATUS
import org.commcare.connect.ConnectConstants.NOTIFICATION_TIME_STAMP
import org.commcare.connect.ConnectConstants.NOTIFICATION_TITLE
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS
import org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID
import org.commcare.connect.ConnectConstants.PAYMENT_ID
import org.commcare.connect.ConnectConstants.PAYMENT_UUID
import org.commcare.connect.ConnectConstants.REDIRECT_ACTION
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.connect.network.connectId.parser.NotificationParseResult
import org.commcare.pn.helper.NotificationBroadcastHelper
import org.commcare.pn.workers.MessagingChannelsKeySyncWorker
import org.commcare.preferences.NotificationPrefs
import org.commcare.util.LogTypes
import org.commcare.utils.coroutines.DispatcherProvider
import org.javarosa.core.services.Logger
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
        CoroutineScope(DispatcherProvider.io()).launch {
            retrieveLatestPushNotifications(context)
                .onSuccess {
                    withContext(DispatcherProvider.main()) {
                        //  switching to main to touch views
                        listener.connectActivityComplete(true)
                    }
                }.onFailure {
                    withContext(DispatcherProvider.main()) {
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
                    //  the user can sign out while the request is in flight, deleting the DB we store into
                    if (!PersonalIdManager.getInstance().isloggedIn()) {
                        continuation.resume(Result.success(emptyList()))
                        return
                    }

                    scheduleMessagingChannelsKeySync(context)
                    CoroutineScope(DispatcherProvider.io()).launch {
                        //  runCatching keeps a storage failure from escaping to the uncaught
                        //  exception handler, and guarantees the continuation is resumed exactly once
                        val result =
                            runCatching {
                                val (savedNotifications, savedNotificationIds) =
                                    processParsedDataIntoDB(context, parseResult)

                                // Update notification preferences and send broadcasts
                                if (savedNotificationIds.isNotEmpty()) {
                                    NotificationPrefs.setNotificationAsUnread(context)
                                }
                                if (savedNotificationIds.isNotEmpty() || parseResult.messagingNotificationIds.isNotEmpty()) {
                                    NotificationBroadcastHelper.sendNewNotificationBroadcast(context)
                                }

                                // Acknowledge all notifications (both stored and messaging)
                                val acknowledged =
                                    acknowledgeNotificationsReceipt(
                                        context,
                                        savedNotificationIds + parseResult.messagingNotificationIds,
                                    )
                                if (!acknowledged) {
                                    //  Not treated as a failure: the notifications are stored, and the
                                    //  server will resend the unacknowledged ones on the next sync
                                    Logger.log(
                                        LogTypes.TYPE_MAINTENANCE,
                                        "Failed to acknowledge receipt of retrieved notifications",
                                    )
                                }

                                savedNotifications
                            }.onFailure {
                                Logger.exception("Error storing retrieved notifications", it)
                            }

                        continuation.resume(result)
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
        if (savedNotificationIds.isEmpty()) {
            return true
        }
        val user = ConnectUserDatabaseUtil.getUser(context) ?: return false
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
        pn.put(OPPORTUNITY_UUID, pnRecord.opportunityUUID)
        pn.put(PAYMENT_UUID, pnRecord.paymentUUID)
        pn.put(PAYMENT_ID, "" + pnRecord.paymentId)
        pn.put(NOTIFICATION_KEY, pnRecord.key)
        pn.put(OPPORTUNITY_STATUS, pnRecord.opportunityStatus)
        pn.put(META_SESSION_ENDPOINT_ID, pnRecord.sessionEndpointId)
        pn.put(META_REQUIRE_APP_SYNC, pnRecord.requireAppSync.toString())
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
