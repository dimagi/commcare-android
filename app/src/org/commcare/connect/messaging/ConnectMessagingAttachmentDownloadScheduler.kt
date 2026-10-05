package org.commcare.connect.messaging

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.commcare.connect.workers.ConnectMessagingAttachmentDownloadWorker
import org.commcare.utils.PushNotificationApiHelper
import java.util.concurrent.TimeUnit

data class ConnectMessagingAttachmentDownloadConditions(
    val networkType: NetworkType,
    val largeFileThresholdBytes: Long,
    val largeFileNetworkType: NetworkType,
) {
    companion object {
        @JvmField
        val DEFAULT =
            ConnectMessagingAttachmentDownloadConditions(
                networkType = NetworkType.CONNECTED,
                largeFileThresholdBytes = 1024L * 1024L,
                largeFileNetworkType = NetworkType.CONNECTED,
            )
    }
}

object ConnectMessagingAttachmentDownloadScheduler {
    private const val WORK_NAME = "connect_messaging_attachment_download"
    private const val SMALL_FILES_WORK_NAME = "${WORK_NAME}_small"
    private const val LARGE_FILES_WORK_NAME = "${WORK_NAME}_large"

    @JvmStatic
    @JvmOverloads
    fun scheduleQueuedDownloads(
        context: Context,
        conditions: ConnectMessagingAttachmentDownloadConditions = ConnectMessagingAttachmentDownloadConditions.DEFAULT,
    ) {
        enqueue(context, conditions, ExistingWorkPolicy.KEEP)
    }

    @JvmStatic
    @JvmOverloads
    fun restartDownloads(
        context: Context,
        conditions: ConnectMessagingAttachmentDownloadConditions = ConnectMessagingAttachmentDownloadConditions.DEFAULT,
    ) {
        enqueue(context, conditions, ExistingWorkPolicy.REPLACE)
    }

    @JvmStatic
    fun cancelDownloads(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(SMALL_FILES_WORK_NAME)
        workManager.cancelUniqueWork(LARGE_FILES_WORK_NAME)
    }

    private fun enqueue(
        context: Context,
        conditions: ConnectMessagingAttachmentDownloadConditions,
        policy: ExistingWorkPolicy,
    ) {
        if (conditions.networkType == conditions.largeFileNetworkType) {
            enqueueWork(context, WORK_NAME, conditions.networkType, 0L..Long.MAX_VALUE, policy)
            return
        }
        enqueueWork(
            context,
            SMALL_FILES_WORK_NAME,
            conditions.networkType,
            0L..conditions.largeFileThresholdBytes,
            policy,
        )
        enqueueWork(
            context,
            LARGE_FILES_WORK_NAME,
            conditions.largeFileNetworkType,
            (conditions.largeFileThresholdBytes + 1)..Long.MAX_VALUE,
            policy,
        )
    }

    private fun enqueueWork(
        context: Context,
        workName: String,
        networkType: NetworkType,
        sizeRange: LongRange,
        policy: ExistingWorkPolicy,
    ) {
        val request =
            OneTimeWorkRequest
                .Builder(ConnectMessagingAttachmentDownloadWorker::class.java)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    PushNotificationApiHelper.SYNC_BACKOFF_DELAY_IN_MINS,
                    TimeUnit.MINUTES,
                ).setInputData(
                    workDataOf(
                        ConnectMessagingAttachmentDownloadWorker.KEY_MIN_SIZE_BYTES to sizeRange.first,
                        ConnectMessagingAttachmentDownloadWorker.KEY_MAX_SIZE_BYTES to sizeRange.last,
                    ),
                ).build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName, policy, request)
    }
}
