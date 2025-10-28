package org.commcare.pn.workermanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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


    companion object{
        const val PN_SYNC_BACKOFF_DELAY_IN_MILLIS: Long = 3 * 60 * 1000L  // min 3 minutes
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

    fun startWorkRequest(notificationPayload: Map<String, String>, action: SyncAction, uniqueWorkName: String) {
        startWorkRequest(notificationPayload, action, uniqueWorkName, syncType)
    }

    fun startWorkRequest(notificationPayload: Map<String, String>, syncAction: SyncAction, uniqueWorkName: String, syncType: SyncType) {
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
                PN_SYNC_BACKOFF_DELAY_IN_MILLIS,
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
