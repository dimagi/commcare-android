package org.commcare.pn.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.MessageManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.connect.ConnectApiHandler
import org.commcare.connect.network.connect.models.ConnectOpportunitiesResponseModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PNApiSyncWorker (val appContext: Context, val workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private var runAttemptCount =0

    private var pnData:Map<String,String>?=null
    private var syncAction:SYNC_ACTION?=null

    companion object {
        const val MAX_RETRIES = 3

        const val PN_DATA = "PN_DATA"

        const val ACTION = "ACTION"

        enum class SYNC_ACTION {
            SYNC_OPPORTUNITY,
            SYNC_PERSONALID_MESSAGING,
            SYNC_DELIVERY_PROGRESS,
            SYNC_LEARNING_PROGRESS
        }
    }


    override suspend fun doWork(): Result {
        val pnApiStatus = startAppropiateSync()
        return if (pnApiStatus.success) {
            Result.success()
        } else if (pnApiStatus.retry && runAttemptCount < MAX_RETRIES) {
            runAttemptCount--
            Result.retry()
        } else {
            return Result.failure()
        }
    }


    private suspend fun startAppropiateSync():PNApiResponseStatus{



        val pnJsonString = inputData.getString(PN_DATA)
        if (pnJsonString != null) {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            pnData = Gson().fromJson(pnJsonString, mapType)
        }


        val syncActionJsonString = inputData.getString(ACTION)
        if (syncActionJsonString != null) {
            val enumType = object : TypeToken<SYNC_ACTION>() {}.type
            syncAction = Gson().fromJson(syncActionJsonString, enumType)
        }

        if(pnData!=null && syncAction!=null){

            return when(syncAction!!){

                SYNC_ACTION.SYNC_OPPORTUNITY->{
                    syncOpportunities()
                }

                SYNC_ACTION.SYNC_PERSONALID_MESSAGING->{
                    syncPersonalIdMessagesOrChannel()
                }

                SYNC_ACTION.SYNC_DELIVERY_PROGRESS->{
                    syncDeliveryProgress()
                }

                SYNC_ACTION.SYNC_LEARNING_PROGRESS->{
                    syncLearningProgress()
                }

            }


        }

        return PNApiResponseStatus(false,false)

    }

    private suspend fun syncOpportunities(): PNApiResponseStatus{
        Log.i("FCMConnectMsgWorker", "Trying to fetch the Opportunities from server")
        val user = ConnectUserDatabaseUtil.getUser(appContext)

        return suspendCoroutine { continuation ->
            val user = ConnectUserDatabaseUtil.getUser(appContext)
            object : ConnectApiHandler<ConnectOpportunitiesResponseModel?>() {
                public override fun onFailure(
                    errorCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?
                ) {
                    Log.i("FCMConnectMsgWorker", "ERROR in fetching Opportunities from the server")
                   continuation.resume(PNApiResponseStatus(false,true))
                }

                public override fun onSuccess(data: ConnectOpportunitiesResponseModel?) {
                   continuation.resume(PNApiResponseStatus(true,false))
                    Log.i("FCMConnectMsgWorker", "Opportunities fetched successfully from the server")
                }
            }.getConnectOpportunities(appContext, user!!)
        }
    }


    private suspend fun syncPersonalIdMessagesOrChannel() : PNApiResponseStatus {
        Log.i("FCMConnectMsgWorker", "Trying to fetch the messages from server")
        return suspendCoroutine { continuation ->

            MessageManager.retrieveMessages(appContext, object : ConnectActivityCompleteListener {
                override fun connectActivityComplete(success: Boolean) {
                    Log.i("FCMConnectMsgWorker", "Messages fetched successfully from server")
                    continuation.resume(PNApiResponseStatus(success,!success))
                }
            })
        }
    }

    private suspend fun syncDeliveryProgress(): PNApiResponseStatus{
        Log.i("FCMConnectMsgWorker", "Trying to fetch the DeliveryProgress from server")
        val opportunityId = pnData?.get(OPPORTUNITY_ID)
        val job = ConnectJobUtils.getCompositeJob(appContext, Integer.parseInt(opportunityId))
        if(job==null) {
            return PNApiResponseStatus(false, false)
        }
        return suspendCoroutine { continuation ->
            ConnectJobHelper.updateDeliveryProgress(appContext,job, object :ConnectActivityCompleteListener{
                override fun connectActivityComplete(success: Boolean) {
                        Log.i("FCMConnectMsgWorker", "DeliveryProgress fetched successfully from server")
                        continuation.resume(PNApiResponseStatus(success,!success))
                }
            })
        }
    }

    private suspend fun syncLearningProgress(): PNApiResponseStatus{
        Log.i("FCMConnectMsgWorker", "Trying to fetch the LearningProgress from server")
        val opportunityId = pnData?.get(OPPORTUNITY_ID)
        val job = ConnectJobUtils.getCompositeJob(appContext, Integer.parseInt(opportunityId))
        if(job==null) {
            return PNApiResponseStatus(false, false)
        }
        return suspendCoroutine { continuation ->
            ConnectJobHelper.updateLearningProgress(appContext,job, object :ConnectActivityCompleteListener{
                override fun connectActivityComplete(success: Boolean) {
                    Log.i("FCMConnectMsgWorker", "LearningProgress fetched successfully from server")
                    continuation.resume(PNApiResponseStatus(success,!success))
                }
            })
        }
    }



}