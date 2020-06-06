package org.commcare.mediadownload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.resources.model.InstallCancelled

class MissingMediaDownloadWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), InstallCancelled {

    override suspend fun doWork(): Result {
        MissingMediaDownloadHelper.installCancelled = this
        kotlin.runCatching {
            MissingMediaDownloadHelper.downloadAllLazyMedia()
        }.onSuccess {
            return Result.success()
        }.onFailure {
            return Result.failure()
        }
        return Result.failure()
    }

    override fun wasInstallCancelled(): Boolean {
        return isStopped
    }
}