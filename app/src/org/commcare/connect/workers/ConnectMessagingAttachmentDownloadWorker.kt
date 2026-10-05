package org.commcare.connect.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.messaging.ConnectMessagingAttachmentDownloader
import org.commcare.connect.messaging.PersonalIdAttachmentFetcher
import org.commcare.utils.coroutines.DispatcherProvider

class ConnectMessagingAttachmentDownloadWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val user = ConnectUserDatabaseUtil.getUser()
        if (!PersonalIdManager.getInstance().isloggedIn() || user == null) {
            return Result.success()
        }
        val sizeRange =
            inputData.getLong(KEY_MIN_SIZE_BYTES, 0L)..inputData.getLong(KEY_MAX_SIZE_BYTES, Long.MAX_VALUE)
        val downloader =
            ConnectMessagingAttachmentDownloader(
                applicationContext,
                PersonalIdAttachmentFetcher(user.userId, user.password),
            )
        val result =
            withContext(DispatcherProvider.io()) {
                ConnectMessagingAttachmentDownloader.passLock.withLock {
                    downloader.runPass(sizeRange) { isStopped }
                }
            }
        return if (result == ConnectMessagingAttachmentDownloader.PassResult.RETRY_LATER) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val KEY_MIN_SIZE_BYTES = "min_size_bytes"
        const val KEY_MAX_SIZE_BYTES = "max_size_bytes"
    }
}
