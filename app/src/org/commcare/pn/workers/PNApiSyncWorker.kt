package org.commcare.pn.workers

import android.content.Context
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
        val pnApiStatus = startAppropriateSync()
        return if (pnApiStatus.success) {
            Result.success()
        } else if (pnApiStatus.retry && runAttemptCount < MAX_RETRIES) {
            runAttemptCount--
            Result.retry()
        } else {
            return Result.failure()
        }
    }


    private suspend fun startAppropriateSync():PNApiResponseStatus{



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
        val user = ConnectUserDatabaseUtil.getUser(appContext)

        return suspendCoroutine { continuation ->
            val user = ConnectUserDatabaseUtil.getUser(appContext)
            object : ConnectApiHandler<ConnectOpportunitiesResponseModel?>() {
                public override fun onFailure(
                    errorCode: PersonalIdOrConnectApiErrorCodes,
                    t: Throwable?
                ) {
                   continuation.resume(PNApiResponseStatus(false,true))
                }

                public override fun onSuccess(data: ConnectOpportunitiesResponseModel?) {
                   continuation.resume(PNApiResponseStatus(true,false))
                }
            }.getConnectOpportunities(appContext, user!!)
        }
    }


    private suspend fun syncPersonalIdMessagesOrChannel() : PNApiResponseStatus {
        return suspendCoroutine { continuation ->

            MessageManager.retrieveMessages(appContext, object : ConnectActivityCompleteListener {
                override fun connectActivityComplete(success: Boolean) {
                    continuation.resume(PNApiResponseStatus(success,!success))
                }
            })
        }
    }

    private suspend fun syncDeliveryProgress(): PNApiResponseStatus{
        val opportunityId = pnData?.get(OPPORTUNITY_ID)
        val job = ConnectJobUtils.getCompositeJob(appContext, Integer.parseInt(opportunityId))
        if(job==null) {
            return PNApiResponseStatus(false, false)
        }
        return suspendCoroutine { continuation ->
            ConnectJobHelper.updateDeliveryProgress(appContext,job, object :ConnectActivityCompleteListener{
                override fun connectActivityComplete(success: Boolean) {
                        continuation.resume(PNApiResponseStatus(success,!success))
                }
            })
        }
    }

    private suspend fun syncLearningProgress(): PNApiResponseStatus{
        val opportunityId = pnData?.get(OPPORTUNITY_ID)
        val job = ConnectJobUtils.getCompositeJob(appContext, Integer.parseInt(opportunityId))
        if(job==null) {
            return PNApiResponseStatus(false, false)
        }
        return suspendCoroutine { continuation ->
            ConnectJobHelper.updateLearningProgress(appContext,job, object :ConnectActivityCompleteListener{
                override fun connectActivityComplete(success: Boolean) {
                    continuation.resume(PNApiResponseStatus(success,!success))
                }
            })
        }
    }



}