package org.commcare.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.commcare.CommCareApplication
import org.commcare.tasks.DataSubmissionListener
import org.commcare.utils.FormUploadResult

class FormSubmissionWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), CancellationChecker, FormSubmissionProgressListener {

    private lateinit var formSubmissionHelper: FormSubmissionHelper

    private val formSubmissionListeners = java.util.ArrayList<DataSubmissionListener>()

    companion object {
        const val Progress = "progress"
    }

    override suspend fun doWork(): Result {
        formSubmissionHelper = FormSubmissionHelper(applicationContext, this, this)
        formSubmissionListeners.add(CommCareApplication.instance().getSession().getListenerForSubmissionNotification())

        val result = formSubmissionHelper.uploadForms()

        return when (result) {
            FormUploadResult.FULL_SUCCESS -> Result.success()
            FormUploadResult.TRANSPORT_FAILURE, FormUploadResult.RATE_LIMITED -> Result.retry()
            else -> Result.failure()
        }
    }

    override fun publishUpdateProgress(vararg progress: Long?) {
        formSubmissionHelper.dispatchProgress(formSubmissionListeners)
        setProgressAsync(workDataOf(Progress to progress))
    }


    override fun wasProcessCancelled(): Boolean {
        return isStopped
    }
}