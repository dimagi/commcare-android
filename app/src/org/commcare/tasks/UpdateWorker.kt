package org.commcare.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import org.commcare.CommCareApplication
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.resources.model.InstallCancelled
import org.commcare.update.UpdateHelper
import org.commcare.update.UpdateProgressListener
import java.lang.Exception

class UpdateWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), InstallCancelled, UpdateProgressListener {


    private lateinit var updateHelper: UpdateHelper

    override suspend fun doWork(): Result {
        var updateResult: ResultAndError<AppInstallStatus>
        try {
            // skip if - An update task is already running | no app is seated | user session is not active
            if (UpdateTask.getRunningInstance() == null &&
                    CommCareApplication.instance().getCurrentApp() != null &&
                    CommCareApplication.instance().getSession().isActive() &&
                    UpdateHelper.shouldAutoUpdate()) {

                updateHelper = UpdateHelper(true, this, this)
                updateHelper.startPinnedNotification(CommCareApplication.instance())
                updateResult = updateHelper.update(ResourceInstallUtils.getDefaultProfileRef())
            } else {
                return Result.success()
            }
        } catch (e: Exception) {
            updateResult = ResultAndError(AppInstallStatus.UnknownFailure, e.message)
        }
        return handleUpdateResult(updateResult)
    }

    private fun handleUpdateResult(updateResult: ResultAndError<AppInstallStatus>): Result {
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