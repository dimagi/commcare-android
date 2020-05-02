package org.commcare.mediadownload

import androidx.work.*
import kotlinx.coroutines.*
import org.commcare.CommCareApplication
import org.commcare.dalvik.R
import org.commcare.engine.resource.AndroidResourceUtils
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.preferences.HiddenPreferences
import org.commcare.resources.model.*
import org.commcare.utils.AndroidCommCarePlatform
import org.commcare.views.dialogs.PinnedNotificationWithProgress
import org.javarosa.core.services.Logger
import org.javarosa.core.util.SizeBoundUniqueVector
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

object MissingMediaDownloadHelper : TableStateListener, InstallCancelled {

    var resourceInProgress: Resource? = null
        private set

    private val jobs = ArrayList<Job>()
    private lateinit var mPinnedNotificationProgress: PinnedNotificationWithProgress


    private const val BACK_OFF_DELAY = 5 * 60 * 1000L // 5 mins
    private const val REQUEST_NAME = "missing_media_download_request"

    var installCancelled: InstallCancelled? = null

    @JvmStatic
    fun scheduleMissingMediaDownload() {
        if (HiddenPreferences.shouldDownloadLazyMediaInBackground()) {
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
    }

    // Returns Unique request name for the UpdateWorker Request
    private fun getMissingMediaDownloadRequestName(): String {
        val appId = CommCareApplication.instance().currentApp.uniqueId
        return REQUEST_NAME + "_" + appId
    }


    fun downloadAllMissingMedia() {
        val platform = CommCareApplication.instance().commCarePlatform
        val global = platform.globalResourceTable

        global.setInstallCancellationChecker(this)

        val problems = Vector<MissingMediaException>()
        global.verifyInstallation(problems, platform)

        val missingMediaResources = SizeBoundUniqueVector<Resource>(problems.size)
        val lazyResources = global.lazyResources

        problems.filter { problem -> problem.type == MissingMediaException.MissingMediaExceptionType.FILE_NOT_FOUND }
                .map { problem ->
                    runCatching {
                        missingMediaResources.add(getLazyResourceFromMediaUri(lazyResources, problem.uri))
                    }.onFailure {
                        Logger.exception(
                                "Could not map to a lazy resource while downloading missing media for uri ${problem.uri}",
                                java.lang.Exception(it))
                    }
                }

        startPinnedNotification()

        missingMediaResources.mapIndexed { index, resource ->
            if (!wasInstallCancelled()) {
                recoverResource(platform, resource)
                incrementProgress(index + 1, missingMediaResources.size)
            }
        }

        cancelNotification()
        global.setInstallCancellationChecker(null)
    }

    @JvmStatic
    fun requestMediaDownload(videoURI: String, missingMediaDownloadListener: MissingMediaDownloadListener) {
        jobs.add(
                CoroutineScope(Dispatchers.Default).launch {
                    var result: MissingMediaDownloadResult = MissingMediaDownloadResult.Error("Unknown Error")
                    try {
                        result = downloadMissingMediaResource(videoURI)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            missingMediaDownloadListener.onComplete(MissingMediaDownloadResult.Exception(e))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        missingMediaDownloadListener.onComplete(result)
                    }
                }
        )
    }

    private fun downloadMissingMediaResource(uri: String): MissingMediaDownloadResult {
        val platform = CommCareApplication.instance().commCarePlatform
        val global = platform.globalResourceTable
        val lazyResources: Vector<Resource> = global.lazyResources!!

        getLazyResourceFromMediaUri(lazyResources, uri).let {

            return if (resourceInProgress == null || it.resourceId != resourceInProgress!!.resourceId) {
                recoverResource(platform, getLazyResourceFromMediaUri(lazyResources, uri))
                MissingMediaDownloadResult.Success
            } else {
                MissingMediaDownloadResult.InProgress
            }
        }
    }

    @Synchronized
    private fun recoverResource(platform: AndroidCommCarePlatform, it: Resource) {
        resourceInProgress = it
        platform.globalResourceTable.recoverResource(it, platform, ResourceInstallUtils.getProfileReference())
        resourceInProgress = null

    }

    private fun getLazyResourceFromMediaUri(lazyResources: Vector<Resource>, uri: String): Resource {
        return lazyResources.first { lazyResource -> AndroidResourceUtils.matchFileUriToResource(lazyResource, uri) }
    }

    fun cancelAllDownloads() {
        jobs.map { job -> job.cancel() }
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
        return installCancelled != null && installCancelled!!.wasInstallCancelled()
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
