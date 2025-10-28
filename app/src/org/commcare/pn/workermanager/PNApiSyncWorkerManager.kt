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
import org.commcare.pn.workers.PNApiSyncWorker
import org.commcare.pn.workers.PNApiSyncWorker.Companion.ACTION
import org.commcare.pn.workers.PNApiSyncWorker.Companion.PN_DATA
import org.commcare.pn.workers.PNApiSyncWorker.Companion.SyncAction
import org.commcare.utils.FirebaseMessagingUtil.cccCheckPassed
import org.commcare.utils.PushNotificationApiHelper.convertPNRecordsToPayload
import java.util.concurrent.TimeUnit

/**
 * This class is responsible for allocating the work request for each type of push notification
 */
class PNApiSyncWorkerManager(val context: Context) {


    companion object{
        const val PN_SYNC_BACKOFF_DELAY_IN_MILLIS: Long = 3 * 60 * 1000L  // min 3 minutes
    }

    enum class SyncType {
        FCM, // Syncs started from FCM push notification
        NOTIFICATION_API // Syncs started from notification screen
    }

    lateinit var pns : ArrayList<Map<String,String>>

    lateinit var syncType: SyncType

    var signaling = false

    /**
     * This can receive the push notification data payload from FCM and notification API.
     */
    constructor(context: Context, pns : ArrayList<Map<String,String>>, syncType: SyncType):this(context){
        this.pns = pns
        this.syncType = syncType
    }

    constructor(context: Context, pnsRecords:List<PushNotificationRecord>?, syncType: SyncType):this(context){
        this.pns = convertPNRecordsToPayload(pnsRecords)
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
        for (pn in pns) {

            when (pn[REDIRECT_ACTION]) {

                CCC_MESSAGE -> {
                    startPersonalIdNotificationsWorker(pn)
                }

                CCC_DEST_PAYMENTS -> {
                    startOpportunitiesSyncWorker(pn)
                    startDeliverySyncWorker(pn)
                }

                CCC_PAYMENT_INFO_CONFIRMATION -> {
                    startOpportunitiesSyncWorker(pn)
                }


                CCC_DEST_OPPORTUNITY_SUMMARY_PAGE -> {
                    startOpportunitiesSyncWorker(pn)
                }


                CCC_DEST_LEARN_PROGRESS -> {
                    startOpportunitiesSyncWorker(pn)
                    startLearningSyncWorker(pn)
                }

                CCC_DEST_DELIVERY_PROGRESS -> {
                    startOpportunitiesSyncWorker(pn)
                    startDeliverySyncWorker(pn)
                }

            }
        }
        return signaling
    }


    private fun startPersonalIdNotificationsWorker(pn:Map<String,String>){
        if(cccCheckPassed(context)) {
            startWorkRequest(
                pn,
                SyncAction.SYNC_PERSONALID_NOTIFICATIONS,
                SyncAction.SYNC_PERSONALID_NOTIFICATIONS.toString()
            )
        }
    }

    private fun startLearningSyncWorker(pn:Map<String,String>){
        if(pn.containsKey(OPPORTUNITY_ID)  && cccCheckPassed(context)){
            val opportunityId = pn.get(OPPORTUNITY_ID)
            startWorkRequest(pn, SyncAction.SYNC_LEARNING_PROGRESS,SyncAction.SYNC_LEARNING_PROGRESS.toString()+"-${opportunityId}")
        }
    }

    private fun startDeliverySyncWorker(pn:Map<String,String>){
        if(pn.containsKey(OPPORTUNITY_ID)  && cccCheckPassed(context)){
            val opportunityId = pn.get(OPPORTUNITY_ID)
            startWorkRequest(pn, SyncAction.SYNC_DELIVERY_PROGRESS,SyncAction.SYNC_DELIVERY_PROGRESS.toString()+"-${opportunityId}")
        }
    }


    private fun startOpportunitiesSyncWorker(pn:Map<String,String>){
        if(cccCheckPassed(context)) {
            startWorkRequest(
                pn,
                SyncAction.SYNC_OPPORTUNITY,
                SyncAction.SYNC_OPPORTUNITY.toString()
            )
        }
    }

    fun startWorkRequest(pn: Map<String,String>, action: SyncAction, uniqueWorkName:String) {
        val pnJsonString = Gson().toJson(pn)
        val syncActionString = Gson().toJson(action)
        val syncTypeString = Gson().toJson(syncType)
        val inputData = Data.Builder()
            .putString(PN_DATA, pnJsonString)
            .putString(ACTION,syncActionString)
            .putString(PNApiSyncWorker.SYNC_TYPE,syncTypeString)
            .build()

        val syncWorkRequest =  OneTimeWorkRequestBuilder<PNApiSyncWorker>()
            .setInputData(inputData)
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
