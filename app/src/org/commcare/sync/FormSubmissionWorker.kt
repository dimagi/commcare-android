package org.commcare.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.update.UpdateHelper
import org.commcare.utils.FormUploadResult

class FormSubmissionWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), CancellationChecker, FormSubmissionProgressListener {

    private lateinit var formSubmissionHelper: FormSubmissionHelper

    override suspend fun doWork(): Result {
        formSubmissionHelper = FormSubmissionHelper(applicationContext, this, this)
        val result = formSubmissionHelper.uploadForms()
        return when (result) {
            FormUploadResult.FULL_SUCCESS -> Result.success()
            FormUploadResult.TRANSPORT_FAILURE -> Result.retry()
            else -> Result.failure()
        }
    }

    override fun publishUpdateProgress(vararg progress: Long?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun wasProcessCancelled(): Boolean {
        return isStopped
    }
}