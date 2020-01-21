package org.commcare.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.resources.model.InstallCancelled
import org.commcare.update.UpdateHelper
import org.commcare.update.UpdateProgressListener

class UpdateWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), InstallCancelled, UpdateProgressListener {


    private lateinit var updateHelper: UpdateHelper

    override suspend fun doWork(): Result {
        // skip if an update task is already running or no app is seated
        if (UpdateTask.getRunningInstance() == null &&
                CommCareApplication.instance().getCurrentApp() != null &&
                CommCareApplication.instance().getSession().isActive() &&
                UpdateHelper.shouldAutoUpdate()) {

            updateHelper = UpdateHelper(true, this, this)
            updateHelper.startPinnedNotification(CommCareApplication.instance())
            val updateResult = updateHelper.update(ResourceInstallUtils.getDefaultProfileRef())
            return handleUpdateResult(updateHelper, updateResult)
        }
        return Result.success()
    }

    private fun handleUpdateResult(updateHelper: UpdateHelper, updateResult: ResultAndError<AppInstallStatus>): Result {
        updateHelper.OnUpdateComplete(updateResult)

        return when (updateResult.data.isUpdateInCompletedState) {
            true -> Result.success()
            else -> Result.retry()
        }
    }


    override fun publishUpdateProgress(complete: Int, total: Int) {
        updateHelper.updateNotification(complete, total)
    }

    override fun wasInstallCancelled(): Boolean {
        return isStopped
    }

}