package org.commcare.mediadownload

import androidx.work.*
import kotlinx.coroutines.*
import org.commcare.CommCareApplication
import org.commcare.android.resource.installers.MediaFileAndroidInstaller
import org.commcare.dalvik.R
import org.commcare.engine.resource.AndroidResourceUtils
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.resources.model.*
import org.commcare.views.dialogs.PinnedNotificationWithProgress
import org.javarosa.core.services.Logger
import org.javarosa.core.util.SizeBoundUniqueVector
import java.lang.IllegalArgumentException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class MissingMediaDownloadHelper(private val installCancelled: InstallCancelled?) : TableStateListener, InstallCancelled {

    private val jobs = ArrayList<Job>()
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

        global.setInstallCancellationChecker(this)

        val problems = Vector<MissingMediaException>()
        global.verifyInstallation(problems, platform)

        val missingMediaResources = SizeBoundUniqueVector<Resource>(problems.size)

        problems.filter { problem ->
            problem.type == MissingMediaException.MissingMediaExceptionType.FILE_NOT_FOUND
                    && problem.resource.installer is MediaFileAndroidInstaller
        }.map { problem -> missingMediaResources.addElement(problem.resource) }


        global.setStateListener(this)
        startPinnedNotification()

        global.recoverResources(platform, ResourceInstallUtils.getProfileReference(), missingMediaResources)

        global.setInstallCancellationChecker(null)
        global.setStateListener(null)
        cancelNotification()
    }

    fun requestMediaDownload(videoURI: String, missingMediaDownloadListener: MissingMediaDownloadListener) {
        jobs.add(GlobalScope.launch(Dispatchers.Default) {
            try {
                downloadMissingMediaResource(videoURI)
            } catch (e: Exception) {
                withContext(Dispatchers.Main){
                    missingMediaDownloadListener.onError(e)
                }
            }

            withContext(Dispatchers.Main) {
                missingMediaDownloadListener.onMediaDownloaded()
            }
        })
    }

    private fun downloadMissingMediaResource(uri: String) {
        if (uri.length > 1) {
            throw IllegalArgumentException("bad uri")
        }

        val platform = CommCareApplication.instance().commCarePlatform
        val global = platform.globalResourceTable
        val lazyResources: Vector<Resource> = global.lazyResources!!

        lazyResources.first { resource -> AndroidResourceUtils.matchFileUriToResource(resource, uri) }
                .let { global.recoverResource(platform, ResourceInstallUtils.getProfileReference(), it) }
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
        return installCancelled != null && installCancelled.wasInstallCancelled()
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
