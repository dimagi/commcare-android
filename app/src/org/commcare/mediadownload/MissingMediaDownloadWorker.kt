package org.commcare.mediadownload

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.resources.model.InstallCancelled

class MissingMediaDownloadWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), InstallCancelled {


    private lateinit var missingMediaDownloadHelper: MissingMediaDownloadHelper

    override suspend fun doWork(): Result {
        missingMediaDownloadHelper = MissingMediaDownloadHelper(this)
        missingMediaDownloadHelper.downloadAllMissingMedia()
        return Result.success()
    }

    override fun wasInstallCancelled(): Boolean {
        return isStopped
    }
}