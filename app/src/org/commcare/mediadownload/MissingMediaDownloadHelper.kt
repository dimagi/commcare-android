package org.commcare.mediadownload

import androidx.work.*
import kotlinx.coroutines.*
import org.commcare.CommCareApplication
import org.commcare.android.resource.installers.MediaFileAndroidInstaller
import org.commcare.dalvik.R
import org.commcare.engine.resource.AndroidResourceUtils
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.engine.resource.installers.LocalStorageUnavailableException
import org.commcare.preferences.HiddenPreferences
import org.commcare.resources.model.*
import org.commcare.tasks.ResultAndError
import org.commcare.update.UpdateHelper
import org.commcare.util.LogTypes
import org.commcare.utils.AndroidCommCarePlatform
import org.commcare.utils.FileUtil
import org.commcare.views.dialogs.PinnedNotificationWithProgress
import org.javarosa.core.services.Logger
import java.lang.Exception
import java.util.concurrent.TimeUnit

// Contains helper functions to download lazy or missing media resources
object MissingMediaDownloadHelper : TableStateListener, InstallCancelled {

    private var resourceInProgress: Resource? = null
    private val jobs = ArrayList<Job>()
    private lateinit var mPinnedNotificationProgress: PinnedNotificationWithProgress

    // constants
    private const val BACK_OFF_DELAY = 5 * 60 * 1000L // 5 mins
    private const val REQUEST_NAME = "missing_media_download_request"

    var installCancelled: InstallCancelled? = null

    // Schedules MissingMediaDownloadWorker
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


    /**
     *
     * Downloads any missing lazy resources, make sure to call this on background thread
     */
    fun downloadAllLazyMedia(): AppInstallStatus {
        val platform = CommCareApplication.instance().commCarePlatform
        val global = platform.globalResourceTable

        global.setInstallCancellationChecker(this)
        startPinnedNotification()

        val lazyResourceIds = global.lazyResourceIds
        lazyResourceIds.asSequence()
                .runCatching {
                    withIndex()
                            .onEach { incrementProgress(it.index + 1, lazyResourceIds.size) }
                            .takeWhile { !wasInstallCancelled() }
                            .map { global.getResource(it.value) }
                            .filter { isResourceMissing(it) }
                            .takeWhile { !wasInstallCancelled() }
                            .onEach { recoverResource(platform, it) }.toList()
                }.onFailure {
                    Logger.log(LogTypes.TYPE_MAINTENANCE, "An error occured while lazy downloading a media resource : " + it.message)
                    return handleRecoverResourceFailure(it).data
                }

        cancelNotification()
        global.setInstallCancellationChecker(null)
        return AppInstallStatus.Installed
    }

    private fun handleRecoverResourceFailure(it: Throwable): ResultAndError<AppInstallStatus> {
        return when (it) {
            is InvalidResourceException -> ResultAndError(AppInstallStatus.InvalidResource, it.message)
            is LocalStorageUnavailableException -> ResultAndError(AppInstallStatus.NoLocalStorage, it.message)
            is UnresolvedResourceException -> ResultAndError(ResourceInstallUtils.processUnresolvedResource(it), it.message)
            is InstallCancelledException -> ResultAndError(AppInstallStatus.Cancelled)
            else -> ResultAndError(AppInstallStatus.UnknownFailure, it.message)
        }
    }

    private fun isResourceMissing(it: Resource): Boolean {
        if (it.installer is MediaFileAndroidInstaller) {
            return !FileUtil.referenceFileExists((it.installer as MediaFileAndroidInstaller).localLocation)
        }
        return false
    }

    /**
     * Downloads a resource with it's location represented by {@param mediaUri}
     */
    @JvmStatic
    fun requestMediaDownload(mediaUri: String, missingMediaDownloadListener: MissingMediaDownloadListener) {
        jobs.add(
                CoroutineScope(Dispatchers.Default).launch {
                    var result: MissingMediaDownloadResult = MissingMediaDownloadResult.Error("Unknown Error")
                    kotlin.runCatching {
                        result = downloadMissingMediaResource(mediaUri)
                    }.onFailure {
                        Logger.exception(" An error occured while recovering a missing resource", it as Exception?)
                        result = MissingMediaDownloadResult.Error(handleRecoverResourceFailure(it).errorMessage)
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
        val lazyResourceIds = global.lazyResourceIds
        lazyResourceIds.asSequence().map { global.getResource(it) }
                .filter { AndroidResourceUtils.matchFileUriToResource(it, uri) }
                .take(1)
                .firstOrNull {
                    return if (it == null) {
                        MissingMediaDownloadResult.Error("Resource not found")
                    } else if (resourceInProgress == null || it.resourceId != resourceInProgress!!.resourceId) {
                        recoverResource(platform, it)
                        MissingMediaDownloadResult.Success
                    } else {
                        MissingMediaDownloadResult.InProgress
                    }
                }
        return MissingMediaDownloadResult.Error("Resource not found")
    }

    // downloads the resource
    @Synchronized
    private fun recoverResource(platform: AndroidCommCarePlatform, it: Resource) {
        resourceInProgress = it
        platform.globalResourceTable.recoverResource(it, platform, ResourceInstallUtils.getProfileReference())
        resourceInProgress = null
    }

    // Cancels all missing media work
    @JvmStatic
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
