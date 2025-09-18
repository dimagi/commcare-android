package org.commcare.pn.workermanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.connect.ConnectConstants.CCC_DEST_DELIVERY_PROGRESS
import org.commcare.connect.ConnectConstants.CCC_DEST_LEARN_PROGRESS
import org.commcare.connect.ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE
import org.commcare.connect.ConnectConstants.CCC_DEST_PAYMENTS
import org.commcare.connect.ConnectConstants.CCC_MESSAGE
import org.commcare.connect.ConnectConstants.CCC_PAYMENT_INFO_CONFIRMATION
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectConstants.REDIRECT_ACTION
import org.commcare.dalvik.R
import org.commcare.pn.workers.PNApiSyncWorker
import org.commcare.pn.workers.PNApiSyncWorker.Companion.ACTION
import org.commcare.pn.workers.PNApiSyncWorker.Companion.PN_DATA
import org.commcare.pn.workers.PNApiSyncWorker.Companion.SYNC_ACTION
import org.commcare.services.FCMMessageData.NOTIFICATION_BODY
import org.commcare.utils.FirebaseMessagingUtil
import org.commcare.utils.FirebaseMessagingUtil.cccCheckPassed
import org.javarosa.core.services.Logger
import java.util.concurrent.TimeUnit

/**
 * This class is responsible for allocating the work request for each type of push notification
 */
class PNApiSyncWorkerManager(val context: Context) {


    companion object{
        val PN_SYNC_BACKOFF_DELAY_IN_MILLIS: Long = 3 * 60 * 1000L  // min 3 minutes

        val SYNC_TYPE_STRING = "SYNC_TYPE_STRING"

    }

    enum class SYNC_TYPE{
        FCM,
        NOTIFICATION_API
    }

    lateinit var pns : ArrayList<Map<String,String>>

    lateinit var syncType: SYNC_TYPE
    val requiredWorkerThread = HashMap<String, WorkRequest>()

    var syncFailedCount = 0
    var syncPassedCount = 0

    /**
     * This can receive the push notification data payload from FCM and notification API.
     */
    constructor(context: Context, pns : ArrayList<Map<String,String>>, syncType: SYNC_TYPE):this(context){
        this.pns = pns
        this.syncType = syncType
        CoroutineScope(Dispatchers.IO).launch { startPNApiSync() }
    }


     fun startPNApiSync() : Boolean {
        createSyncWorkerRequest()
        if(requiredWorkerThread.isNotEmpty()){
            for(workRequest in requiredWorkerThread){
                CoroutineScope(Dispatchers.IO).launch {
                    startListeningToWorkerRequest(workRequest.value)
                }
            }
        }
        return requiredWorkerThread.isNotEmpty()
    }

    fun createSyncWorkerRequest(){
        for(pn in pns){

            when(pn[REDIRECT_ACTION]){

                null , "" -> {
                    continue
                }


                CCC_MESSAGE ->{
                    createPersonalIdMessagingSyncWorkRequest(pn)
                }

                CCC_DEST_PAYMENTS->{
                    createOpportunitiesSyncWorkRequest(pn)
                    createDeliverySyncWorkRequest(pn)
                }

                CCC_PAYMENT_INFO_CONFIRMATION->{
                    createOpportunitiesSyncWorkRequest(pn)
                }


                CCC_DEST_OPPORTUNITY_SUMMARY_PAGE -> {
                    createOpportunitiesSyncWorkRequest(pn)
                }


                CCC_DEST_LEARN_PROGRESS -> {
                    createOpportunitiesSyncWorkRequest(pn)
                    createLearningSyncWorkRequest(pn)
                }

                CCC_DEST_DELIVERY_PROGRESS -> {
                    createOpportunitiesSyncWorkRequest(pn)
                    createDeliverySyncWorkRequest(pn)
                }

            }
        }
    }

    private suspend fun startListeningToWorkerRequest(workRequest: WorkRequest){

        WorkManager.getInstance(context).enqueue(workRequest)
        WorkManager.getInstance(context).getWorkInfoByIdFlow(workRequest.id)
            .collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            processAfterSuccessfulSync(workInfo)
                        }

                        WorkInfo.State.FAILED -> {
                            processAfterSyncFailed(workInfo)
                        }

                        WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> {
                            processAfterSyncFailed(workInfo)
                        }

                        else -> {
                            // Work is still running or in other states
                        }
                    }
                }
            }
    }


    private fun createPersonalIdMessagingSyncWorkRequest(pn:Map<String,String>){
        if(!requiredWorkerThread.containsKey(CCC_MESSAGE) && cccCheckPassed(context)){
            requiredWorkerThread.put(CCC_MESSAGE,getWorkRequest(pn,
                SYNC_ACTION.SYNC_PERSONALID_MESSAGING
            ))
        }
    }

    private fun createLearningSyncWorkRequest(pn:Map<String,String>){
        if(pn.containsKey(OPPORTUNITY_ID)  && cccCheckPassed(context)){
            val opportunityId = pn.get(OPPORTUNITY_ID)
            if(!requiredWorkerThread.containsKey(SYNC_ACTION.SYNC_LEARNING_PROGRESS.toString() +"-${opportunityId}")){
                requiredWorkerThread.put(SYNC_ACTION.SYNC_LEARNING_PROGRESS.toString()+"-${opportunityId}",getWorkRequest(pn,
                    SYNC_ACTION.SYNC_LEARNING_PROGRESS))
            }
        }
    }

    private fun createDeliverySyncWorkRequest(pn:Map<String,String>){
        if(pn.containsKey(OPPORTUNITY_ID)  && cccCheckPassed(context)){
            val opportunityId = pn.get(OPPORTUNITY_ID)
            if(!requiredWorkerThread.containsKey(SYNC_ACTION.SYNC_DELIVERY_PROGRESS.toString() +"-${opportunityId}")){
                requiredWorkerThread.put(SYNC_ACTION.SYNC_DELIVERY_PROGRESS.toString()+"-${opportunityId}",getWorkRequest(pn,
                    SYNC_ACTION.SYNC_DELIVERY_PROGRESS))
            }
        }
    }


    private fun createOpportunitiesSyncWorkRequest(pn:Map<String,String>){
        if(!requiredWorkerThread.containsKey(SYNC_ACTION.SYNC_OPPORTUNITY.toString()) && cccCheckPassed(context)){
            requiredWorkerThread.put(SYNC_ACTION.SYNC_OPPORTUNITY.toString() ,getWorkRequest(pn,
                SYNC_ACTION.SYNC_OPPORTUNITY))
        }
    }

    fun getWorkRequest(pn: Map<String,String>,action: SYNC_ACTION): WorkRequest {

        val pnJsonString = Gson().toJson(pn)
        val syncActionString = Gson().toJson(action)
        val inputData = Data.Builder()
            .putString(PN_DATA, pnJsonString)
            .putString(ACTION,syncActionString)
            .build()

         return OneTimeWorkRequestBuilder<PNApiSyncWorker>()
             .setInputData(inputData)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                PN_SYNC_BACKOFF_DELAY_IN_MILLIS,
                TimeUnit.MILLISECONDS
            ).build()

    }

    private fun processAfterSuccessfulSync(workInfo: WorkInfo){
        syncPassedCount++
        if(syncPassedCount==requiredWorkerThread.size && syncType== SYNC_TYPE.FCM) {
            val dataPayload = getPNDataPayload(workInfo)
            if (dataPayload != null) {
                raiseFCMPushNotification(dataPayload)
            }
        }
    }

    private fun processAfterSyncFailed(workInfo: WorkInfo){
        Logger.exception("WorkRequest Failed to complete the task-${workInfo.stopReason}", Throwable("WorkRequest Failed with ${workInfo.stopReason}"))
        syncFailedCount++
        if(syncType == SYNC_TYPE.FCM && syncFailedCount ==1){   // raise the notification on first failure and not multiple times
            val dataPayload = getPNDataPayload(workInfo)
            if (dataPayload != null) {
                dataPayload.put(
                    NOTIFICATION_BODY,
                    context.getString(R.string.fcm_sync_failed_body_text)
                )
                raiseFCMPushNotification(dataPayload)
            }
        }
    }

    private fun raiseFCMPushNotification(dataPayload:Map<String,String>){
        FirebaseMessagingUtil.handleNotification(context,dataPayload,null,true)
    }

    private fun getPNDataPayload(workInfo: WorkInfo):HashMap<String,String>?{
        val pnJsonString = workInfo.outputData.getString(PN_DATA)
        if (pnJsonString != null) {
            val mapType = object : TypeToken<HashMap<String, Any>>() {}.type
            return Gson().fromJson(pnJsonString, mapType)
        }
        return null
    }


}