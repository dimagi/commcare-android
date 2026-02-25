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
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE_NEW
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING
import org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_LEARNING
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectConstants.NOTIFICATION_BODY
import org.commcare.connect.ConnectConstants.NOTIFICATION_ID
import org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS
import org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS_DELIVERY
import org.commcare.connect.ConnectConstants.OPPORTUNITY_STATUS_LEARN
import org.commcare.connect.ConnectConstants.OPPORTUNITY_UUID
import org.commcare.connect.ConnectJobHelper
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.NotificationRecordDatabaseHelper.getNotificationById
import org.commcare.dalvik.R
import org.commcare.util.LogTypes
import org.commcare.utils.FirebaseMessagingUtil
import org.commcare.utils.FirebaseMessagingUtil.cccCheckPassed
import org.commcare.utils.PushNotificationApiHelper
import org.javarosa.core.services.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * This worker is responsible to sync different API endpoints from Connect and PersonalID server based on the action
 * specified in the input data.
 */
class NotificationsSyncWorker(
    val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private var notificationPayload: HashMap<String, String>? = null
    private var syncAction: SyncAction? = null

    private var showNotification: Boolean = false

    private var job: ConnectJobRecord? = null

    companion object {
        const val MAX_RETRIES = 3

        const val NOTIFICATION_PAYLOAD = "PN_DATA"

        const val ACTION = "ACTION"

        const val SHOW_NOTIFICATION_KEY = "show_notification_key"

        enum class SyncAction {
            SYNC_OPPORTUNITY,
            SYNC_PERSONALID_NOTIFICATIONS,
            SYNC_DELIVERY_PROGRESS,
            SYNC_LEARNING_PROGRESS,
            SYNC_UPDATE_PROGRESS,
        }
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            initStateFromInputData()
            val syncResult = startAppropriateSync()
            logResult(syncResult)
            if (syncResult.success) {
                processAfterSuccessfulSync()
                Result.success(workDataOf(NOTIFICATION_PAYLOAD to Gson().toJson(notificationPayload)))
            } else if (syncResult.retry && runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                processAfterSyncFailed()
                Result.failure()
            }
        }

    private fun logResult(syncResult: PNApiResponseStatus) {
        val actionStr = syncAction?.toString()
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Sync Action: $actionStr completed with success: ${syncResult?.success}")
    }

    private fun initStateFromInputData() {
        val notificationPayloadJson = inputData.getString(NOTIFICATION_PAYLOAD)
        if (notificationPayloadJson != null) {
            val mapType = object : TypeToken<HashMap<String, Any>>() {}.type
            notificationPayload = Gson().fromJson<HashMap<String, String>>(notificationPayloadJson, mapType)
        }

        val syncActionStr = inputData.getString(ACTION)
        requireNotNull(syncActionStr) { "Sync action cannot be null" }
        syncAction = SyncAction.valueOf(syncActionStr)

        showNotification = inputData.getBoolean(SHOW_NOTIFICATION_KEY, false)

        if (showNotification) {
            requireNotNull(notificationPayload) { "Notification payload can't be null when we want to show a notification" }
        }
    }

    private suspend fun startAppropriateSync(): PNApiResponseStatus =
        when (syncAction!!) {
            SyncAction.SYNC_OPPORTUNITY -> {
                if (cccCheckPassed(appContext)) syncOpportunities() else getFailedResponseWithoutRetry()
            }

            SyncAction.SYNC_PERSONALID_NOTIFICATIONS -> {
                if (cccCheckPassed(appContext)) syncPersonalIdNotifications() else getFailedResponseWithoutRetry()
            }

            SyncAction.SYNC_DELIVERY_PROGRESS, SyncAction.SYNC_LEARNING_PROGRESS, SyncAction.SYNC_UPDATE_PROGRESS -> {
                if (cccCheckPassed(appContext)) syncJobProgress() else getFailedResponseWithoutRetry()
            }
        }

    private suspend fun syncOpportunities(): PNApiResponseStatus =
        suspendCoroutine { continuation ->
            ConnectJobHelper.retrieveOpportunities(
                appContext,
                object : ConnectActivityCompleteListener {
                    override fun connectActivityComplete(
                        success: Boolean,
                        error: String?,
                    ) {
                        continuation.resume(PNApiResponseStatus(success, !success))
                    }
                },
            )
        }

    private suspend fun syncPersonalIdNotifications(): PNApiResponseStatus {
        val result = PushNotificationApiHelper.retrieveLatestPushNotifications(appContext)
        return PNApiResponseStatus(result.isSuccess, result.isFailure)
    }

    private suspend fun syncJobProgress(): PNApiResponseStatus {
        job = getConnectJob()
        return if (job == null) {
            handleNoConnectJob()
        } else {
            suspendCoroutine { continuation ->
                ConnectJobHelper.updateJobProgress(
                    appContext,
                    job!!,
                    false,
                    null,
                    object : ConnectActivityCompleteListener {
                        override fun connectActivityComplete(
                            success: Boolean,
                            error: String?,
                        ) {
                            continuation.resume(PNApiResponseStatus(success, !success))
                        }
                    },
                )
            }
        }
    }

    private fun checkForOpportunityStatus(): Boolean =
        if (notificationPayload?.contains(OPPORTUNITY_STATUS) == true && job != null) {
            val opportunityStatus = notificationPayload?.get(OPPORTUNITY_STATUS)
            (job?.status == STATUS_LEARNING || job?.status == STATUS_AVAILABLE || job?.status == STATUS_AVAILABLE_NEW) &&
                OPPORTUNITY_STATUS_LEARN.equals(
                    opportunityStatus,
                ) ||
                job?.status == STATUS_DELIVERING &&
                OPPORTUNITY_STATUS_DELIVERY.equals(
                    opportunityStatus,
                )
        } else {
            true
        }

    private fun getConnectJob(): ConnectJobRecord? {
        val opportunityUUID = notificationPayload?.get(OPPORTUNITY_UUID)
        if (!TextUtils.isEmpty(opportunityUUID)) {
            return ConnectJobUtils.getCompositeJob(
                appContext,
                opportunityUUID!!,
            )
        }
        return null
    }

    private fun handleNoConnectJob(): PNApiResponseStatus {
        Logger.exception(
            "WorkRequest Failed to complete the task for -$syncAction as connect job not found",
            Throwable("WorkRequest Failed for $syncAction as connect job not found"),
        )
        return getFailedResponseWithoutRetry()
    }

    private fun getFailedResponseWithoutRetry() = PNApiResponseStatus(false, false)

    private fun processAfterSuccessfulSync() {
        raiseFCMPushNotificationIfApplicable()
    }

    private fun processAfterSyncFailed() {
        Logger.exception(
            "WorkRequest Failed to complete the task for -$syncAction",
            Throwable("WorkRequest Failed for $syncAction"),
        )
        notificationPayload?.put(
            NOTIFICATION_BODY,
            appContext.getString(R.string.fcm_sync_failed_body_text),
        )
        raiseFCMPushNotificationIfApplicable()
    }

    private fun raiseFCMPushNotificationIfApplicable() {
        if (showNotification && !isNotificationRead() && checkForOpportunityStatus()) {
            FirebaseMessagingUtil.handleNotification(appContext, notificationPayload, null, true)
        }
    }

    private fun isNotificationRead(): Boolean {
        return if (notificationPayload?.containsKey(NOTIFICATION_ID)!! &&
            notificationPayload?.get(NOTIFICATION_ID) != null
        ) {
            val notification =
                getNotificationById(appContext, notificationPayload?.get(NOTIFICATION_ID)!!)
            return notification != null && notification.readStatus
        } else {
            false
        }
    }
}
