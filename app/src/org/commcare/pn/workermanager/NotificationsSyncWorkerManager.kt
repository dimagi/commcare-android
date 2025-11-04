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
import org.commcare.android.database.connect.models.PushNotificationRecord
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
import org.commcare.utils.PushNotificationApiHelper.convertPNRecordsToPayload
import java.util.concurrent.TimeUnit

/**
 * This class is responsible for allocating the work request for each type of push notification
 */
class NotificationsSyncWorkerManager(val context: Context) {

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
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val inputData = Data.Builder()
                .putString(ACTION, SyncAction.SYNC_PERSONALID_NOTIFICATIONS.toString())
                .build()

            val retrievalRequest = PeriodicWorkRequest.Builder(
                NotificationsSyncWorker::class.java,
                PERIODICITY_FOR_NOTIFICATION_RETRIEVAL_IN_HOURS,
                TimeUnit.HOURS
            )
                .setInputData(inputData)
                .addTag(PERIODIC_NOTIFICATION_WORKER_TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    PERIODIC_NOTIFICATION__BACKOFF,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NOTIFICATION_REQUEST_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                retrievalRequest
            )
        }

        /**
         * Cancels the periodic push notification retrieval work
         * @param context Application context
         */
        @JvmStatic
        fun cancelPeriodicPushNotificationRetrieval(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(
                PERIODIC_NOTIFICATION_REQUEST_NAME
            )
        }

        fun schedulePushNotificationRetrievalWith(context: Context, delay: Long = 0) {
            val notificationSyncWorkerManager =
                NotificationsSyncWorkerManager(context, null, false, true)
            notificationSyncWorkerManager.startPersonalIdNotificationsWorker(
                emptyMap(),
                false,
                delay
            )
        }
    }

    lateinit var notificationsPayload: ArrayList<Map<String, String>>

    private var showNotification = false
    private var syncNotification = false

    var signaling = false

    /**
     * This can receive the push notification data payload from FCM and notification API.
     */
    constructor(
        context: Context,
        notificationsPayload: ArrayList<Map<String, String>>,
        showNotification: Boolean,
        syncNotification: Boolean = false
    ) : this(context) {
        this.notificationsPayload = notificationsPayload
        this.showNotification = showNotification
        this.syncNotification = syncNotification
    }

    constructor(
        context: Context,
        pnsRecords: List<PushNotificationRecord>?,
        showNotification: Boolean,
        syncNotification: Boolean = false
    ) : this(context) {
        this.notificationsPayload = convertPNRecordsToPayload(pnsRecords)
        this.showNotification = showNotification
        this.syncNotification = syncNotification
    }

    /**
     * This method will start Api sync for received PNs either through FCM or notification API
     * @return whether a sync was scheduled as part of this call
     */
    fun startPNApiSync(): Boolean {
        return startSyncWorker()
    }

    private fun startSyncWorker(): Boolean {
        var isNotificationSyncScheduled = false
        for (notificationPayload in notificationsPayload) {
            when (notificationPayload[REDIRECT_ACTION]) {
                CCC_MESSAGE -> {
                    startPersonalIdNotificationsWorker(notificationPayload)
                    isNotificationSyncScheduled = true
                }

                CCC_DEST_PAYMENTS -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                    startDeliverySyncWorker(notificationPayload)
                }

                CCC_PAYMENT_INFO_CONFIRMATION -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                }

                CCC_DEST_OPPORTUNITY_SUMMARY_PAGE -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                }

                CCC_DEST_LEARN_PROGRESS -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                    startLearningSyncWorker(notificationPayload)
                }

                CCC_DEST_DELIVERY_PROGRESS -> {
                    startOpportunitiesSyncWorker(notificationPayload)
                    startDeliverySyncWorker(notificationPayload)
                }
            }
        }
        if (syncNotification && !isNotificationSyncScheduled) {
            // we want to get info on pending notifications irrespective of whether there are notification related FCMs or not
            startPersonalIdNotificationsWorker(emptyMap(), false)
        }
        return signaling
    }

    private fun startPersonalIdNotificationsWorker(
        notificationPayload: Map<String, String>,
        showNotification: Boolean = this.showNotification,
        delay:Long=0
    ) {
        if (cccCheckPassed(context)) {
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_PERSONALID_NOTIFICATIONS,
                SyncAction.SYNC_PERSONALID_NOTIFICATIONS.toString(),
                showNotification,
                delay
            )
        }
    }

    private fun startLearningSyncWorker(notificationPayload: Map<String, String>) {
        if (notificationPayload.containsKey(OPPORTUNITY_ID) && cccCheckPassed(context)) {
            val opportunityId = notificationPayload.get(OPPORTUNITY_ID)
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_LEARNING_PROGRESS,
                SyncAction.SYNC_LEARNING_PROGRESS.toString() + "-$opportunityId"
            )
        }
    }

    private fun startDeliverySyncWorker(notificationPayload: Map<String, String>) {
        if (notificationPayload.containsKey(OPPORTUNITY_ID) && cccCheckPassed(context)) {
            val opportunityId = notificationPayload.get(OPPORTUNITY_ID)
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_DELIVERY_PROGRESS,
                SyncAction.SYNC_DELIVERY_PROGRESS.toString() + "-$opportunityId"
            )
        }
    }

    private fun startOpportunitiesSyncWorker(notificationPayload: Map<String, String>) {
        if (cccCheckPassed(context)) {
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_OPPORTUNITY,
                SyncAction.SYNC_OPPORTUNITY.toString()
            )
        }
    }

    private fun startWorkRequest(
        notificationPayload: Map<String, String>,
        syncAction: SyncAction,
        uniqueWorkName: String,
        showNotification: Boolean = this.showNotification,
        delay: Long = 0
    ) {
        val inputDataBuilder = Data.Builder()
            .putString(ACTION, syncAction.toString())
            .putBoolean(NotificationsSyncWorker.SHOW_NOTIFICATION_KEY, showNotification)

        if (!notificationPayload.isEmpty()) {
            val pnJsonString = Gson().toJson(notificationPayload)
            inputDataBuilder.putString(NOTIFICATION_PAYLOAD, pnJsonString)
        }

        val syncWorkRequest = OneTimeWorkRequestBuilder<NotificationsSyncWorker>()
            .setInputData(inputDataBuilder.build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                SYNC_BACKOFF_DELAY_IN_MINS,
                TimeUnit.MINUTES
            ).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            syncWorkRequest
        )
        signaling = true
    }
}
