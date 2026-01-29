package org.commcare.pn.workermanager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.gson.Gson
import org.commcare.connect.ConnectConstants.CCC_DEST_DELIVERY_PROGRESS
import org.commcare.connect.ConnectConstants.CCC_DEST_LEARN_PROGRESS
import org.commcare.connect.ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE
import org.commcare.connect.ConnectConstants.CCC_DEST_PAYMENTS
import org.commcare.connect.ConnectConstants.CCC_MESSAGE
import org.commcare.connect.ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectConstants.REDIRECT_ACTION
import org.commcare.connect.PersonalIdManager
import org.commcare.pn.workers.NotificationsSyncWorker
import org.commcare.pn.workers.NotificationsSyncWorker.Companion.ACTION
import org.commcare.pn.workers.NotificationsSyncWorker.Companion.NOTIFICATION_PAYLOAD
import org.commcare.pn.workers.NotificationsSyncWorker.Companion.SyncAction
import org.commcare.utils.FirebaseMessagingUtil.cccCheckPassed
import java.util.concurrent.TimeUnit

/**
 * This class is responsible for allocating the work request for each type of push notification
 */
class NotificationsSyncWorkerManager(
    val context: Context,
) {
    companion object {
        private const val SYNC_BACKOFF_DELAY_IN_MINS: Long = 3
        private const val PERIODIC_NOTIFICATION_WORKER_TAG = "periodic_notification_worker"
        private const val PERIODICITY_FOR_NOTIFICATION_RETRIEVAL_IN_HOURS = 1L
        private const val PERIODIC_NOTIFICATION__BACKOFF = 10L
        private const val PERIODIC_NOTIFICATION_REQUEST_NAME = "periodic_notification_request_name"

        /**
         * Schedules periodic push notification retrieval to run every hour only if Personal ID login is present
         * @param context Application context
         */
        @JvmStatic
        fun schedulePeriodicPushNotificationRetrievalChecked(context: Context) {
            val perosnalIdManager = PersonalIdManager.getInstance()
            perosnalIdManager.init(context)
            if (perosnalIdManager.isloggedIn()) {
                schedulePeriodicPushNotificationRetrieval(context)
            }
        }

        /**
         * Schedules periodic push notification retrieval to run every hour
         * @param context Application context
         */
        @JvmStatic
        fun schedulePeriodicPushNotificationRetrieval(context: Context) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val inputData =
                Data
                    .Builder()
                    .putString(ACTION, SyncAction.SYNC_PERSONALID_NOTIFICATIONS.toString())
                    .build()

            val retrievalRequest =
                PeriodicWorkRequest
                    .Builder(
                        NotificationsSyncWorker::class.java,
                        PERIODICITY_FOR_NOTIFICATION_RETRIEVAL_IN_HOURS,
                        TimeUnit.HOURS,
                    ).setInputData(inputData)
                    .addTag(PERIODIC_NOTIFICATION_WORKER_TAG)
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        PERIODIC_NOTIFICATION__BACKOFF,
                        TimeUnit.MINUTES,
                    ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NOTIFICATION_REQUEST_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                retrievalRequest,
            )
        }

        /**
         * Cancels the periodic push notification retrieval work
         * @param context Application context
         */
        @JvmStatic
        fun cancelPeriodicPushNotificationRetrieval(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(
                PERIODIC_NOTIFICATION_REQUEST_NAME,
            )
        }

        fun schedulePushNotificationRetrievalWith(
            context: Context,
            delay: Long = 0,
        ) {
            val notificationSyncWorkerManager =
                NotificationsSyncWorkerManager(
                    context,
                    showNotification = false,
                    syncNotification = true,
                )
            notificationSyncWorkerManager.startPersonalIdNotificationsWorker(emptyMap(), false, delay)
        }
    }

    private var showNotification = false
    private var syncNotification = false
    private var isNotificationSyncScheduled = false

    /**
     * This can receive the push notification data payload from FCM and notification API.
     * @param context - android context
     * @param showNotification - decide whether to show a notification or not after sync is successful.
     * @param syncNotification - decide to pull / sync any pending notifications or not.
     */
    constructor(
        context: Context,
        showNotification: Boolean,
        syncNotification: Boolean,
    ) : this(context) {
        this.syncNotification = syncNotification
        this.showNotification = showNotification
    }

    private fun parseAndStartNotificationWorker(notificationPayload: Map<String, String>?): Boolean {
        var notificationHandled = false
        notificationPayload?.let {
            when (notificationPayload[REDIRECT_ACTION]) {
                CCC_MESSAGE -> {
                    startPersonalIdNotificationsWorker(notificationPayload)
                    notificationHandled = true
                    isNotificationSyncScheduled = true
                }

                CCC_DEST_PAYMENTS -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                    startDeliverySyncWorker(notificationPayload)
                    notificationHandled = true
                }

                CCC_PAYMENT_INFO_CONFIRMATION -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                    notificationHandled = true
                }

                CCC_DEST_OPPORTUNITY_SUMMARY_PAGE -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                    notificationHandled = true
                }

                CCC_DEST_LEARN_PROGRESS -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                    startLearningSyncWorker(notificationPayload)
                    notificationHandled = true
                }

                CCC_DEST_DELIVERY_PROGRESS -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                    startDeliverySyncWorker(notificationPayload)
                    notificationHandled = true
                }
            }
        }
        return notificationHandled
    }

    /**
     * This method will start sync for received payload from FCM
     * @return whether the notification was handled in this call
     */
    fun startSyncWorker(notificationPayload: Map<String, String>?): Boolean {
        val notificationHandled = parseAndStartNotificationWorker(notificationPayload)
        syncNotificationIfRequired()
        return notificationHandled
    }

    /**
     * This method will start sync for received payloads from notification API
     */
    fun startSyncWorkers(notificationsPayload: ArrayList<Map<String, String>>?) {
        notificationsPayload?.let {
            for (notificationPayload in notificationsPayload) {
                parseAndStartNotificationWorker(notificationPayload)
            }
        }
        syncNotificationIfRequired()
    }

    // we want to get info on pending notifications irrespective of whether there are notification related FCMs or not
    private fun syncNotificationIfRequired() {
        if (syncNotification && !isNotificationSyncScheduled) {
            startPersonalIdNotificationsWorker(emptyMap(), false)
        }
    }

    private fun startPersonalIdNotificationsWorker(
        notificationPayload: Map<String, String>,
        showNotification: Boolean = this.showNotification,
        delay: Long = 0,
    ) {
        if (cccCheckPassed(context)) {
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_PERSONALID_NOTIFICATIONS,
                SyncAction.SYNC_PERSONALID_NOTIFICATIONS.toString(),
                showNotification,
                delay,
            )
        }
    }

    private fun startLearningSyncWorker(notificationPayload: Map<String, String>) {
        if (notificationPayload.containsKey(OPPORTUNITY_ID) && cccCheckPassed(context)) {
            val opportunityId = notificationPayload.get(OPPORTUNITY_ID)
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_LEARNING_PROGRESS,
                SyncAction.SYNC_LEARNING_PROGRESS.toString() + "-$opportunityId",
            )
        }
    }

    private fun startDeliverySyncWorker(notificationPayload: Map<String, String>) {
        if (notificationPayload.containsKey(OPPORTUNITY_ID) && cccCheckPassed(context)) {
            val opportunityId = notificationPayload.get(OPPORTUNITY_ID)
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_DELIVERY_PROGRESS,
                SyncAction.SYNC_DELIVERY_PROGRESS.toString() + "-$opportunityId",
            )
        }
    }

    private fun startOpportunitiesSyncWorker(notificationPayload: Map<String, String>) {
        if (cccCheckPassed(context)) {
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_OPPORTUNITY,
                SyncAction.SYNC_OPPORTUNITY.toString(),
            )
        }
    }

    private fun startWorkRequest(
        notificationPayload: Map<String, String>,
        syncAction: SyncAction,
        uniqueWorkName: String,
        showNotification: Boolean = this.showNotification,
        delay: Long = 0,
    ) {
        val inputDataBuilder =
            Data
                .Builder()
                .putString(ACTION, syncAction.toString())
                .putBoolean(NotificationsSyncWorker.SHOW_NOTIFICATION_KEY, showNotification)

        if (!notificationPayload.isEmpty()) {
            val pnJsonString = Gson().toJson(notificationPayload)
            inputDataBuilder.putString(NOTIFICATION_PAYLOAD, pnJsonString)
        }

        val syncWorkRequest =
            OneTimeWorkRequestBuilder<NotificationsSyncWorker>()
                .setInputData(inputDataBuilder.build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    SYNC_BACKOFF_DELAY_IN_MINS,
                    TimeUnit.MINUTES,
                ).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            syncWorkRequest,
        )
    }
}
