package org.commcare.pn.workers

import android.content.Context
import android.text.TextUtils
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectConstants.NOTIFICATION_BODY
import org.commcare.connect.ConnectConstants.OPPORTUNITY_ID
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.MessageManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.dalvik.R
import org.commcare.pn.workermanager.PNApiSyncWorkerManager
import org.commcare.pn.workermanager.PNApiSyncWorkerManager.SYNC_TYPE
import org.commcare.pn.workermanager.NotificationsRetrievalWorkerManager
import org.commcare.utils.FirebaseMessagingUtil
import org.commcare.utils.FirebaseMessagingUtil.cccCheckPassed
import org.javarosa.core.services.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PNApiSyncWorker (val appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private var pnData: HashMap<String,String>?=null
    private var syncAction:SYNC_ACTION?=null

    private var syncType: SYNC_TYPE?=null

    companion object {
        const val MAX_RETRIES = 3

        const val PN_DATA = "PN_DATA"

        const val ACTION = "ACTION"

        const val SYNC_TYPE = "SYNC_TYPE"

        enum class SYNC_ACTION {
            SYNC_OPPORTUNITY,
            SYNC_PERSONALID_MESSAGING,
            SYNC_DELIVERY_PROGRESS,
            SYNC_LEARNING_PROGRESS
        }
    }



    override suspend fun doWork(): Result = withContext(Dispatchers.IO){
        val pnApiStatus = startAppropriateSync()
        if (pnApiStatus.success) {
            processAfterSuccessfulSync()
            Result.success(workDataOf(PN_DATA to Gson().toJson(pnData)))
        } else if (pnApiStatus.retry && runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            processAfterSyncFailed()
            Result.failure()
        }
    }


    private suspend fun startAppropriateSync():PNApiResponseStatus{

        val pnJsonString = inputData.getString(PN_DATA)
        if (pnJsonString != null) {
            val mapType = object : TypeToken<HashMap<String, Any>>() {}.type
            pnData = Gson().fromJson<HashMap<String, String>>(pnJsonString, mapType)
        }


        val syncActionJsonString = inputData.getString(ACTION)
        if (syncActionJsonString != null) {
            val enumType = object : TypeToken<SYNC_ACTION>() {}.type
            syncAction = Gson().fromJson(syncActionJsonString, enumType)
        }

        val syncTypeString = inputData.getString(SYNC_TYPE)
        if(syncTypeString!=null){
            val enumType = object : TypeToken<SYNC_TYPE>() {}.type
            syncType = Gson().fromJson(syncTypeString, enumType)
        }

        requireNotNull(pnData){"PN data cannot be null"}
        requireNotNull(syncAction){"Sync action cannot be null"}



        return when(syncAction!!){

            SYNC_ACTION.SYNC_OPPORTUNITY->{
                if(cccCheckPassed(appContext)) syncOpportunities() else getFailedResponseWithoutRetry()
            }

            SYNC_ACTION.SYNC_PERSONALID_MESSAGING->{
                if(cccCheckPassed(appContext)) syncPersonalIdMessagesOrChannel() else getFailedResponseWithoutRetry()
            }

            SYNC_ACTION.SYNC_DELIVERY_PROGRESS->{
                if(cccCheckPassed(appContext)) syncDeliveryProgress() else getFailedResponseWithoutRetry()
            }

            SYNC_ACTION.SYNC_LEARNING_PROGRESS->{
                if(cccCheckPassed(appContext)) syncLearningProgress()  else getFailedResponseWithoutRetry()
            }

        }
    }

    private suspend fun syncOpportunities(): PNApiResponseStatus = suspendCoroutine { continuation ->
        ConnectJobHelper.retrieveOpportunities(appContext, object :ConnectActivityCompleteListener{
            override fun connectActivityComplete(success: Boolean) {
                continuation.resume(PNApiResponseStatus(success,!success))
            }
        })
    }



    private suspend fun syncPersonalIdMessagesOrChannel() : PNApiResponseStatus = suspendCoroutine { continuation ->
        MessageManager.retrieveMessages(appContext, object : ConnectActivityCompleteListener {
            override fun connectActivityComplete(success: Boolean) {
                continuation.resume(PNApiResponseStatus(success,!success))
            }
        })
    }


    private suspend fun syncDeliveryProgress(): PNApiResponseStatus{
        val job = getConnectJob()
        if(job==null) {
            Logger.exception("WorkRequest Failed to complete the task for -${syncAction} as connect job not found", Throwable("WorkRequest Failed for ${syncAction} as connect job not found"))
            return getFailedResponseWithoutRetry()
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
        val job = getConnectJob()
        if(job == null) {
            Logger.exception("WorkRequest Failed to complete the task for -${syncAction} as connect job not found", Throwable("WorkRequest Failed for ${syncAction} as connect job not found"))
            return getFailedResponseWithoutRetry()
        }
        return suspendCoroutine { continuation ->
            ConnectJobHelper.updateLearningProgress(appContext,job, object :ConnectActivityCompleteListener{
                override fun connectActivityComplete(success: Boolean) {
                    continuation.resume(PNApiResponseStatus(success,!success))
                }
            })
        }
    }

    private fun getConnectJob(): ConnectJobRecord?{
        val opportunityId = pnData?.get(OPPORTUNITY_ID)
        return if(TextUtils.isEmpty(opportunityId)) null else ConnectJobUtils.getCompositeJob(appContext, Integer.parseInt(opportunityId!!))
    }

    private fun getFailedResponseWithoutRetry() = PNApiResponseStatus(false,false)


    private fun processAfterSuccessfulSync(){
        raiseFCMPushNotificationIfApplicable()
        NotificationsRetrievalWorkerManager.scheduleImmediateNotificationRetrieval(appContext)
    }

    private fun processAfterSyncFailed(){
        Logger.exception("WorkRequest Failed to complete the task for -${syncAction}", Throwable("WorkRequest Failed for ${syncAction}"))
        pnData?.put(
            NOTIFICATION_BODY,
            appContext.getString(R.string.fcm_sync_failed_body_text)
        )
        raiseFCMPushNotificationIfApplicable()
    }

    private fun raiseFCMPushNotificationIfApplicable(){
        if(PNApiSyncWorkerManager.SYNC_TYPE.FCM == syncType) {
            FirebaseMessagingUtil.handleNotification(appContext, pnData, null, true)
        }
    }

}