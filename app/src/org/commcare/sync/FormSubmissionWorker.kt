package org.commcare.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class FormSubmissionWorker (appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {


    override suspend fun doWork(): Result {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}