package org.commcare.mediadownload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.resources.model.InstallCancelled

class MissingMediaDownloadWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), InstallCancelled {

    override suspend fun doWork(): Result {
        MissingMediaDownloadHelper.installCancelled = this
        val result = MissingMediaDownloadHelper.downloadAllLazyMedia()
        return if (result == AppInstallStatus.Installed) Result.success() else Result.retry()
    }

    override fun wasInstallCancelled(): Boolean {
        return isStopped
    }
}