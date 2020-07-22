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
import org.commcare.util.LogTypes
import org.commcare.utils.AndroidCommCarePlatform
import org.commcare.utils.FileUtil
import org.commcare.utils.StringUtils
import org.commcare.views.dialogs.PinnedNotificationWithProgress
import org.commcare.views.notifications.NotificationMessage
import org.commcare.views.notifications.NotificationMessageFactory
import org.javarosa.core.services.Logger
import org.javarosa.core.services.locale.Localization
import java.util.concurrent.TimeUnit

// Contains helper functions to download lazy or missing media resources
object MissingMediaDownloadHelper : TableStateListener, InstallCancelled {

    private var mResourceInProgressListener: MissingMediaDownloadListener? = null
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


    /**
     *
     * Downloads any missing lazy resources, make sure to call this on background thread
     */
    suspend fun downloadAllLazyMedia(): AppInstallStatus {
        if (!HiddenPreferences.isLazyMediaDownloadComplete()) {
            val platform = CommCareApplication.instance().commCarePlatform
            val global = platform.globalResourceTable

            global.setInstallCancellationChecker(installCancelled)
            startPinnedNotification()

            val lazyResourceIds = global.lazyResourceIds

            try {
                lazyResourceIds.asSequence()
                        .withIndex()
                        .onEach { incrementProgress(it.index + 1, lazyResourceIds.size) }
                        .takeWhile { !wasInstallCancelled() }
                        .map { global.getResource(it.value) }
                        .filter { isResourceMissing(it) }
                        .takeWhile { !wasInstallCancelled() }
                        .onEach {
                            runBlocking {
                                recoverResource(platform, it)
                            }
                        }.toList()
            } catch (e: Exception) {
                Logger.log(LogTypes.TYPE_MAINTENANCE, "An error occured while lazy downloading a media resource : " + e.message)
                return handleRecoverResourceFailure(e).data
            } finally {
                cancelNotification()
                global.setInstallCancellationChecker(null)
            }

            HiddenPreferences.setLazyMediaDownloadComplete(true)
        }
        return AppInstallStatus.Installed
    }

    private fun handleRecoverResourceFailure(it: Throwable): ResultAndError<AppInstallStatus> {
        val notificationMessage: NotificationMessage = when (it) {
            is InvalidResourceException -> NotificationMessageFactory.message(
                    AppInstallStatus.InvalidResource,
                    arrayOf(null, it.resourceName, it.message))
            is LocalStorageUnavailableException -> NotificationMessageFactory.message(
                    AppInstallStatus.NoLocalStorage,
                    arrayOf(null, null, it.message))
            is UnresolvedResourceException ->  NotificationMessageFactory.message(
                    ResourceInstallUtils.processUnresolvedResource(it),
                    arrayOf(null, it.resource.descriptor, it.message))
            is InstallCancelledException -> NotificationMessageFactory.message(
                    AppInstallStatus.Cancelled,
                    arrayOf(null, null, it.message))
            else ->  NotificationMessageFactory.message(AppInstallStatus.UnknownFailure, arrayOf(null, null, it.message))
        }
        CommCareApplication.notificationManager().reportNotificationMessage(notificationMessage)
        return ResultAndError(AppInstallStatus.InvalidResource, Localization.get(notificationMessage!!.title))
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

                    // If the download is already in Progress save the listener to notify once the download completes
                    if (result == MissingMediaDownloadResult.InProgress) {
                        mResourceInProgressListener = missingMediaDownloadListener
                    }

                    withContext(Dispatchers.Main) {
                        missingMediaDownloadListener.onComplete(result)
                    }
                }
        )
    }


    private suspend fun downloadMissingMediaResource(uri: String): MissingMediaDownloadResult {
        val platform = CommCareApplication.instance().commCarePlatform
        val global = platform.globalResourceTable
        val lazyResourceIds = global.lazyResourceIds
        lazyResourceIds.asSequence().map { global.getResource(it) }
                .filter { AndroidResourceUtils.matchFileUriToResource(it, uri) }
                .take(1)
                .firstOrNull {
                    return if (it == null) {
                        MissingMediaDownloadResult.Error(StringUtils.getStringRobust(CommCareApplication.instance(), R.string.media_not_found_error))
                    } else if (resourceInProgress == null || it.resourceId != resourceInProgress!!.resourceId) {
                        if (!FileUtil.referenceFileExists(uri)) {
                            recoverResource(platform, it)
                        }
                        MissingMediaDownloadResult.Success
                    } else {
                        MissingMediaDownloadResult.InProgress
                    }
                }
        return MissingMediaDownloadResult.Error(StringUtils.getStringRobust(CommCareApplication.instance(), R.string.media_not_found_error))
    }

    // downloads the resource
    @Synchronized
    private suspend fun recoverResource(platform: AndroidCommCarePlatform, it: Resource) {
        resourceInProgress = it
        var result: MissingMediaDownloadResult = MissingMediaDownloadResult.Error("Unknown error")
        try {
            platform.globalResourceTable.recoverResource(it, platform, ResourceInstallUtils.getProfileReference())
            result = MissingMediaDownloadResult.Success
        } catch (e: Exception) {
            result = MissingMediaDownloadResult.Error(handleRecoverResourceFailure(e).errorMessage)
            throw e
        } finally {
            if (mResourceInProgressListener != null) {
                withContext(Dispatchers.Main) {
                    mResourceInProgressListener!!.onComplete(result)
                }
            }
            resourceInProgress = null
            mResourceInProgressListener = null
        }
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
