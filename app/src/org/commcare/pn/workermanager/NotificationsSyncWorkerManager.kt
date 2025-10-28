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
        private const val SYNC_BACKOFF_DELAY_IN_MILLIS: Long = 3 * 60 * 1000L  // min 3 minutes
        private const val NOTIFICATION_RETRIEVAL_PERIODIC_WORKER_TAG = "notification_retrieval_periodic_worker"
        private const val PERIODICITY_FOR_NOTIFICATION_RETRIEVAL_IN_HOURS = 1L
        private const val NOTIFICATION_RETRIEVAL_BACKOFF_DELAY_FOR_RETRIEVAL_RETRY = 10L
        private const val NOTIFICATION_RETRIEVAL_PERIODIC_REQUEST_NAME = "notification_retrieval_periodic_request"

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
                .putString(NotificationsSyncWorker.SYNC_TYPE, SyncType.OTHER.toString())
                .build()

            val retrievalRequest = PeriodicWorkRequest.Builder(
                NotificationsSyncWorker::class.java,
                PERIODICITY_FOR_NOTIFICATION_RETRIEVAL_IN_HOURS,
                TimeUnit.HOURS
            )
                .setInputData(inputData)
                .addTag(NOTIFICATION_RETRIEVAL_PERIODIC_WORKER_TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    NOTIFICATION_RETRIEVAL_BACKOFF_DELAY_FOR_RETRIEVAL_RETRY,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NOTIFICATION_RETRIEVAL_PERIODIC_REQUEST_NAME,
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
                NOTIFICATION_RETRIEVAL_PERIODIC_REQUEST_NAME
            )
        }
    }

    enum class SyncType {
        FCM, // Syncs from a push notification and should result into a user visible notification post-sync
        OTHER // Syncs that don't need a user visible notification post-sync
    }

    lateinit var notificationsPayload : ArrayList<Map<String,String>>

    lateinit var syncType: SyncType

    var signaling = false

    /**
     * This can receive the push notification data payload from FCM and notification API.
     */
    constructor(context: Context, notificationsPayload : ArrayList<Map<String,String>>, syncType: SyncType):this(context){
        this.notificationsPayload = notificationsPayload
        this.syncType = syncType
    }

    constructor(context: Context, pnsRecords:List<PushNotificationRecord>?, syncType: SyncType):this(context){
        this.notificationsPayload = convertPNRecordsToPayload(pnsRecords)
        this.syncType = syncType
    }


    /**
     * This method will start Api sync for received PNs either through FCM or notification API
     * @return whether a sync was scheduled as part of this call
     */
    fun startPNApiSync() : Boolean {
        return startSyncWorker()
    }

    private fun startSyncWorker() : Boolean {
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
        if (!isNotificationSyncScheduled) {
            // we want to get info on pending notifications irrespective of whether there are notification related FCMs or not
            startPersonalIdNotificationsWorker(emptyMap())
        }
        return signaling
    }


    private fun startPersonalIdNotificationsWorker(notificationPayload:Map<String,String>){
        if(cccCheckPassed(context)) {
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_PERSONALID_NOTIFICATIONS,
                SyncAction.SYNC_PERSONALID_NOTIFICATIONS.toString(),
                SyncType.OTHER
            )
        }
    }

    private fun startLearningSyncWorker(notificationPayload:Map<String,String>){
        if(notificationPayload.containsKey(OPPORTUNITY_ID)  && cccCheckPassed(context)){
            val opportunityId = notificationPayload.get(OPPORTUNITY_ID)
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_LEARNING_PROGRESS,
                SyncAction.SYNC_LEARNING_PROGRESS.toString()+"-${opportunityId}"
            )
        }
    }

    private fun startDeliverySyncWorker(notificationPayload:Map<String,String>){
        if(notificationPayload.containsKey(OPPORTUNITY_ID)  && cccCheckPassed(context)){
            val opportunityId = notificationPayload.get(OPPORTUNITY_ID)
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_DELIVERY_PROGRESS,
                SyncAction.SYNC_DELIVERY_PROGRESS.toString()+"-${opportunityId}"
            )
        }
    }


    private fun startOpportunitiesSyncWorker(notificationPayload:Map<String,String>){
        if(cccCheckPassed(context)) {
            startWorkRequest(
                notificationPayload,
                SyncAction.SYNC_OPPORTUNITY,
                SyncAction.SYNC_OPPORTUNITY.toString()
            )
        }
    }

    private fun startWorkRequest(notificationPayload: Map<String, String>, action: SyncAction, uniqueWorkName: String) {
        startWorkRequest(notificationPayload, action, uniqueWorkName, syncType)
    }

    private fun startWorkRequest(notificationPayload: Map<String, String>, syncAction: SyncAction, uniqueWorkName: String, syncType: SyncType) {
        val inputDataBuilder = Data.Builder()
            .putString(ACTION, syncAction.toString())
            .putString(NotificationsSyncWorker.SYNC_TYPE, syncType.toString())

        if (!notificationPayload.isEmpty()) {
            val pnJsonString = Gson().toJson(notificationPayload)
            inputDataBuilder.putString(NOTIFICATION_PAYLOAD, pnJsonString)
        }

        val syncWorkRequest =  OneTimeWorkRequestBuilder<NotificationsSyncWorker>()
            .setInputData(inputDataBuilder.build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                SYNC_BACKOFF_DELAY_IN_MILLIS,
                TimeUnit.MILLISECONDS
            ).build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            syncWorkRequest
        )
        
        signaling=true

    }
}
