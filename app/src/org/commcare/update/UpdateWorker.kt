package org.commcare.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.commcare.CommCareApplication
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.InstallCancelled
import org.commcare.resources.model.InstallRequestSource
import org.commcare.tasks.ResultAndError
import org.javarosa.core.services.Logger

/**
 * Used to stage an update for the seated app in the background. Does not perform
 * actual update.
 *
 */
class UpdateWorker(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams), InstallCancelled, UpdateProgressListener {

    companion object {
        const val Progress_Complete = "complete"
        const val Progress_Total = "total"
    }

    private lateinit var updateHelper: UpdateHelper

    override suspend fun doWork(): Result {

        updateHelper = UpdateHelper.getNewInstance(true, this, this)

        return coroutineScope {
            val job = async {
                doUpdateWork()
            }

            job.invokeOnCompletion { exception: Throwable? ->
                when {
                    exception is CancellationException -> handleUpdateResult(ResultAndError(AppInstallStatus.Cancelled))
                    exception != null -> {
                        Logger.exception("Unknown error while app update", exception);
                        handleUpdateResult(ResultAndError(AppInstallStatus.UnknownFailure))
                    }
                }
            }

            job.await()
        }

    }

    private fun doUpdateWork(): Result {
        val updateResult: ResultAndError<AppInstallStatus>

        // skip if - An update task is already running | no app is seated | user session is not active
        if (UpdateTask.getRunningInstance() == null &&
                CommCareApplication.instance().currentApp != null &&
                CommCareApplication.instance().session.isActive) {

            updateHelper.startPinnedNotification(CommCareApplication.instance())
            updateResult = updateHelper.update(ResourceInstallUtils.getDefaultProfileRef(),
                    ResourceInstallContext(InstallRequestSource.BACKGROUND_UPDATE))
        } else {
            return Result.success()
        }
        return handleUpdateResult(updateResult)
    }

    private fun handleUpdateResult(updateResult: ResultAndError<AppInstallStatus>): Result {
        if (updateResult.data == AppInstallStatus.Cancelled) {
            updateHelper.OnUpdateCancelled()
        }

        updateHelper.OnUpdateComplete(updateResult)
        cleanUp()

        return when {
            updateResult.data == AppInstallStatus.UpdateStaged -> Result.success()
            updateResult.data.shouldRetryUpdate() -> Result.retry()
            else -> Result.failure()
        }
    }

    private fun cleanUp() {
        updateHelper.clearInstance()
    }


    override fun publishUpdateProgress(complete: Int, total: Int) {
        updateHelper.updateNotification(complete, total)
        setProgressAsync(workDataOf(
                Progress_Complete to complete,
                Progress_Total to total))
    }

    override fun wasInstallCancelled(): Boolean {
        return isStopped
    }

}
