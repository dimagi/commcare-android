package org.commcare.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.update.UpdateHelper

class FormSubmissionWorker (appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), CancellationChecker, FormSubmissionProgressListener{

    private lateinit var formSubmissionHelper: FormSubmissionHelper

    override suspend fun doWork(): Result {
        formSubmissionHelper = FormSubmissionHelper(applicationContext,this, this)
        return Result.failure()
    }

    override fun publishUpdateProgress(vararg progress: Long?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun wasProcessCancelled(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}