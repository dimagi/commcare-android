package org.commcare.connect.messaging

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.commcare.connect.database.ConnectMessagingAttachmentDatabaseHelper
import org.commcare.connect.workers.ConnectMessagingAttachmentDownloadWorker
import org.commcare.preferences.ConnectMessagingPreferences
import org.commcare.preferences.ConnectMessagingPreferences.AttachmentAutoDownload
import org.commcare.utils.FirebaseMessagingUtil
import org.commcare.utils.PushNotificationApiHelper
import java.util.concurrent.TimeUnit

data class ConnectMessagingAttachmentDownloadConditions(
    val networkType: NetworkType,
    val largeFileThresholdBytes: Long,
    val largeFileNetworkType: NetworkType,
) {
    companion object {
        const val LARGE_FILE_THRESHOLD_BYTES = 1024L * 1024L

        @JvmStatic
        fun fromPreferences(context: Context): ConnectMessagingAttachmentDownloadConditions? {
            val largeFileNetworkType =
                when (ConnectMessagingPreferences.getAttachmentAutoDownload(context)) {
                    AttachmentAutoDownload.MANUAL_ONLY -> return null
                    AttachmentAutoDownload.LARGE_ON_WIFI_ONLY -> NetworkType.UNMETERED
                    AttachmentAutoDownload.ANY_NETWORK -> NetworkType.CONNECTED
                }
            return ConnectMessagingAttachmentDownloadConditions(
                networkType = NetworkType.CONNECTED,
                largeFileThresholdBytes = LARGE_FILE_THRESHOLD_BYTES,
                largeFileNetworkType = largeFileNetworkType,
            )
        }
    }
}

object ConnectMessagingAttachmentDownloadScheduler {
    private const val WORK_TAG = "connect_messaging_attachment_download"
    const val ALL_FILES_WORK_NAME = WORK_TAG
    const val SMALL_FILES_WORK_NAME = "${WORK_TAG}_small"
    const val LARGE_FILES_WORK_NAME = "${WORK_TAG}_large"
    private const val REQUESTED_WORK_NAME_PREFIX = "${WORK_TAG}_requested_"
    private const val REQUESTED_RETRY_WORK_NAME_PREFIX = "${WORK_TAG}_retry_"

    @JvmStatic
    fun scheduleQueuedDownloads(context: Context) {
        enqueueAutomaticDownloads(context, ExistingWorkPolicy.KEEP)
    }

    @JvmStatic
    fun applyAutoDownloadSetting(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(ALL_FILES_WORK_NAME)
        workManager.cancelUniqueWork(SMALL_FILES_WORK_NAME)
        workManager.cancelUniqueWork(LARGE_FILES_WORK_NAME)
        if (ConnectMessagingPreferences.isAutomaticDownloadEnabled(context)) {
            enqueueAutomaticDownloads(context, ExistingWorkPolicy.REPLACE)
        } else {
            ConnectMessagingAttachmentDatabaseHelper.returnPendingAttachmentsToWaiting()
            LocalBroadcastManager
                .getInstance(context)
                .sendBroadcast(Intent(FirebaseMessagingUtil.MESSAGING_UPDATE_BROADCAST))
        }
    }

    @JvmStatic
    fun downloadNow(
        context: Context,
        attachmentId: String,
    ) {
        WorkManager.getInstance(context).cancelUniqueWork(requestedRetryWorkName(attachmentId))
        enqueueWork(
            context,
            requestedWorkName(attachmentId),
            NetworkType.NOT_REQUIRED,
            requestedData(attachmentId, isBackgroundRetry = false),
            ExistingWorkPolicy.REPLACE,
        )
    }

    fun retryRequestedDownloadLater(
        context: Context,
        attachmentId: String,
    ) {
        enqueueWork(
            context,
            requestedRetryWorkName(attachmentId),
            NetworkType.CONNECTED,
            requestedData(attachmentId, isBackgroundRetry = true),
            ExistingWorkPolicy.REPLACE,
            PushNotificationApiHelper.SYNC_BACKOFF_DELAY_IN_MINS,
        )
    }

    @JvmStatic
    fun cancelDownloads(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    fun requestedWorkName(attachmentId: String) = REQUESTED_WORK_NAME_PREFIX + attachmentId

    fun requestedRetryWorkName(attachmentId: String) = REQUESTED_RETRY_WORK_NAME_PREFIX + attachmentId

    private fun requestedData(
        attachmentId: String,
        isBackgroundRetry: Boolean,
    ): Data =
        workDataOf(
            ConnectMessagingAttachmentDownloadWorker.KEY_ATTACHMENT_ID to attachmentId,
            ConnectMessagingAttachmentDownloadWorker.KEY_BACKGROUND_RETRY to isBackgroundRetry,
        )

    private fun enqueueAutomaticDownloads(
        context: Context,
        policy: ExistingWorkPolicy,
    ) {
        val conditions = ConnectMessagingAttachmentDownloadConditions.fromPreferences(context) ?: return
        if (conditions.networkType == conditions.largeFileNetworkType) {
            enqueueWork(context, ALL_FILES_WORK_NAME, conditions.networkType, sizeRangeData(0L..Long.MAX_VALUE), policy)
            return
        }
        enqueueWork(
            context,
            SMALL_FILES_WORK_NAME,
            conditions.networkType,
            sizeRangeData(0L..conditions.largeFileThresholdBytes),
            policy,
        )
        enqueueWork(
            context,
            LARGE_FILES_WORK_NAME,
            conditions.largeFileNetworkType,
            sizeRangeData((conditions.largeFileThresholdBytes + 1)..Long.MAX_VALUE),
            policy,
        )
    }

    private fun sizeRangeData(sizeRange: LongRange): Data =
        workDataOf(
            ConnectMessagingAttachmentDownloadWorker.KEY_MIN_SIZE_BYTES to sizeRange.first,
            ConnectMessagingAttachmentDownloadWorker.KEY_MAX_SIZE_BYTES to sizeRange.last,
        )

    private fun enqueueWork(
        context: Context,
        workName: String,
        networkType: NetworkType,
        inputData: Data,
        policy: ExistingWorkPolicy,
        initialDelayMinutes: Long = 0,
    ) {
        val request =
            OneTimeWorkRequest
                .Builder(ConnectMessagingAttachmentDownloadWorker::class.java)
                .addTag(WORK_TAG)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    PushNotificationApiHelper.SYNC_BACKOFF_DELAY_IN_MINS,
                    TimeUnit.MINUTES,
                ).setInputData(inputData)
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName, policy, request)
    }
}
