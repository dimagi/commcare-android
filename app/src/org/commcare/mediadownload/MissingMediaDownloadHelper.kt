package org.commcare.mediadownload

import androidx.work.*
import org.commcare.CommCareApplication
import org.commcare.dalvik.R
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.resources.model.*
import org.commcare.views.dialogs.PinnedNotificationWithProgress
import java.util.*
import java.util.concurrent.TimeUnit

class MissingMediaDownloadHelper(private val installCancelled: InstallCancelled) : TableStateListener, InstallCancelled {

    private lateinit var mPinnedNotificationProgress: PinnedNotificationWithProgress

    companion object {

        private const val BACK_OFF_DELAY = 5 * 60 * 1000L // 5 mins
        private const val REQUEST_NAME = "missing_media_download_request"

        @JvmStatic
        fun scheduleMissingMediaDownload() {
            val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()

            val downloadMissingMediaRequest = OneTimeWorkRequest.Builder(MissingMediaDownloadWorker::class.java)
                    .addTag(CommCareApplication.instance().currentApp.appRecord.applicationId)
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            BACK_OFF_DELAY,
                            TimeUnit.MILLISECONDS)
                    .build()

            WorkManager.getInstance(CommCareApplication.instance())
                    .enqueueUniqueWork(
                            getMissingMediaDownloadRequestName(),
                            ExistingWorkPolicy.KEEP,
                            downloadMissingMediaRequest)
        }

        // Returns Unique request name for the UpdateWorker Request
        private fun getMissingMediaDownloadRequestName(): String {
            val appId = CommCareApplication.instance().currentApp.uniqueId
            return REQUEST_NAME + "_" + appId
        }
    }


    fun downloadAllMissingMedia() {
        val platform = CommCareApplication.instance().commCarePlatform

        val global = platform.globalResourceTable
        val problems = Vector<MissingMediaException>()

        global.setInstallCancellationChecker(this)

        global.verifyInstallation(problems, platform)

        val missingResources = Vector<Resource>(problems.size)
        for (problem in problems) {
            if (problem.type == MissingMediaException.MissingMediaExceptionType.FILE_NOT_FOUND) {
                missingResources.addElement(problem.resource)
            }
        }

        global.setStateListener(this)
        startPinnedNotification()

        global.recoverResources(platform, ResourceInstallUtils.getProfileReference(), missingResources)

        global.setInstallCancellationChecker(null)
        global.setStateListener(null)
        cancelNotification()
    }

    override fun incrementProgress(complete: Int, total: Int) {
        updateNotification(complete, total)
    }

    override fun compoundResourceAdded(table: ResourceTable?) {
        // Do nothing
    }

    override fun simpleResourceAdded() {
        // Do nothing
    }

    override fun wasInstallCancelled(): Boolean {
        return installCancelled.wasInstallCancelled()
    }


    private fun startPinnedNotification() {
        mPinnedNotificationProgress = PinnedNotificationWithProgress(CommCareApplication.instance(), "media.pinned.download",
                "media.pinned.progress", R.drawable.update_download_icon)
    }


    private fun updateNotification(complete: Int, total: Int) {
        mPinnedNotificationProgress.handleTaskUpdate(complete, total)
    }

    private fun cancelNotification() {
        mPinnedNotificationProgress.handleTaskCancellation()
    }
}
