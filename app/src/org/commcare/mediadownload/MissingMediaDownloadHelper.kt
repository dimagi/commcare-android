package org.commcare.mediadownload

import androidx.annotation.WorkerThread
import androidx.work.*
import kotlinx.coroutines.*
import org.commcare.AppUtils.getCurrentAppId
import org.commcare.CommCareApplication
import org.commcare.android.resource.installers.MediaFileAndroidInstaller
import org.commcare.dalvik.R
import org.commcare.engine.resource.AndroidResourceUtils
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.engine.resource.installers.LocalStorageUnavailableException
import org.commcare.network.RequestStats
import org.commcare.preferences.HiddenPreferences
import org.commcare.resources.ResourceInstallContext
import org.commcare.resources.model.*
import org.commcare.tasks.ResultAndError
import org.commcare.utils.AndroidCommCarePlatform
import org.commcare.utils.FileUtil
import org.commcare.utils.StringUtils
import org.commcare.views.dialogs.PinnedNotificationWithProgress
import org.commcare.views.notifications.NotificationMessage
import org.commcare.views.notifications.NotificationMessageFactory
import org.javarosa.core.services.Logger
import java.util.concurrent.TimeUnit

// Contains helper functions to download lazy or missing media resources
object MissingMediaDownloadHelper : TableStateListener {

    @Volatile
    private var mResourceInProgressListener: MissingMediaDownloadListener? = null

    @Volatile
    private var resourceInProgress: Resource? = null

    private val jobs = ArrayList<Job>()
    private lateinit var mPinnedNotificationProgress: PinnedNotificationWithProgress<AppInstallStatus>

    // constants
    private const val BACK_OFF_DELAY = 5 * 60 * 1000L // 5 mins
    private const val REQUEST_NAME = "missing_media_download_request"

    // Schedules MissingMediaDownloadWorker
    @JvmStatic
    fun scheduleMissingMediaDownload() {
        val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        val downloadMissingMediaRequest = OneTimeWorkRequest.Builder(MissingMediaDownloadWorker::class.java)
                .addTag(getCurrentAppId())
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
        return REQUEST_NAME + "_" + getCurrentAppId()
    }


    /**
     *
     * Downloads any missing lazy resources
     */
    @WorkerThread
    suspend fun downloadAllLazyMedia(cancellationChecker: InstallCancelled): AppInstallStatus {
        if (!HiddenPreferences.isLazyMediaDownloadComplete()) {
            val platform = CommCareApplication.instance().commCarePlatform
            val global = platform.globalResourceTable

            RequestStats.register(InstallRequestSource.BACKGROUND_LAZY_RESOURCE)
            global.setInstallCancellationChecker(cancellationChecker)

            startPinnedNotification()

            val lazyResourceIds = global.lazyResourceIds

            try {
                val failure = lazyResourceIds.asSequence()
                        .withIndex()
                        .onEach { incrementProgress(it.index + 1, lazyResourceIds.size) }
                        .takeWhile { !cancellationChecker.wasInstallCancelled() }
                        .map { global.getResource(it.value) }
                        .filter { isResourceMissing(it) }
                        .takeWhile { !cancellationChecker.wasInstallCancelled() }
                        .map { runBlocking { recoverResource(platform, it, InstallRequestSource.BACKGROUND_LAZY_RESOURCE) } }
                        .firstOrNull { it.data != AppInstallStatus.Installed }

                return if (failure != null) {
                    failure.data
                } else {
                    HiddenPreferences.setLazyMediaDownloadComplete(true)
                    RequestStats.markSuccess(InstallRequestSource.BACKGROUND_LAZY_RESOURCE)
                    AppInstallStatus.Installed
                }

            } catch (e: Exception) {
                return handleRecoverResourceFailure(e).data
            } finally {
                cancelNotification()
                global.setInstallCancellationChecker(null)
            }
        }
        return AppInstallStatus.Installed
    }

    private fun handleRecoverResourceFailure(it: Throwable): ResultAndError<AppInstallStatus> {
        val appInstallStatus: AppInstallStatus
        val notificationMessage: NotificationMessage = when (it) {
            is InvalidResourceException -> {
                appInstallStatus = AppInstallStatus.InvalidResource
                NotificationMessageFactory.message(appInstallStatus, arrayOf(null, it.resourceName, it.message))
            }

            is LocalStorageUnavailableException -> {
                appInstallStatus = AppInstallStatus.NoLocalStorage
                NotificationMessageFactory.message(appInstallStatus, arrayOf(null, null, it.message))
            }

            is UnresolvedResourceException -> {
                appInstallStatus = ResourceInstallUtils.processUnresolvedResource(it)
                NotificationMessageFactory.message(appInstallStatus, arrayOf(null, it.resource.descriptor, it.message))
            }

            is InstallCancelledException -> {
                appInstallStatus = AppInstallStatus.Cancelled
                NotificationMessageFactory.message(appInstallStatus, arrayOf(null, null, it.message))
            }

            else -> {
                appInstallStatus = AppInstallStatus.UnknownFailure
                NotificationMessageFactory.message(appInstallStatus, arrayOf(null, null, it.message))
            }
        }
        CommCareApplication.notificationManager().reportNotificationMessage(notificationMessage)
        Logger.exception("An error occured while lazy downloading a media resource", it)
        return ResultAndError(appInstallStatus, notificationMessage!!.title)
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
    fun requestMediaDownload(mediaUri: String, defaultDispatcher: CoroutineDispatcher,
                             missingMediaDownloadListener: MissingMediaDownloadListener) {
        jobs.add(
                CoroutineScope(defaultDispatcher).launch {
                    var result: MissingMediaDownloadResult = MissingMediaDownloadResult.Error("Unknown Error")
                    kotlin.runCatching {
                        result = downloadMissingMediaResource(mediaUri)
                    }.onFailure {
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


    private fun downloadMissingMediaResource(uri: String): MissingMediaDownloadResult {
        val platform = CommCareApplication.instance().commCarePlatform
        val global = platform.globalResourceTable
        val lazyResourceIds = global.allResourceIds
        var result = lazyResourceIds.asSequence()
                .map { global.getResource(it) }
                .filter { it != null }
                .filter { AndroidResourceUtils.matchFileUriToResource(it, uri) }
                .take(1)
                .map { runBlocking { downloadResource(it) } }
                .firstOrNull()

        if (result == null) {
            result = MissingMediaDownloadResult.Error(StringUtils.getStringRobust(
                    CommCareApplication.instance(),
                    R.string.media_not_found_error))
        }
        return result
    }

    private suspend fun downloadResource(it: Resource): MissingMediaDownloadResult {
        return if (resourceInProgress == null || it.resourceId != resourceInProgress!!.resourceId) {
            RequestStats.register(InstallRequestSource.FOREGROUND_LAZY_RESOURCE)
            val resultAndError = recoverResource(CommCareApplication.instance().commCarePlatform,
                    it, InstallRequestSource.FOREGROUND_LAZY_RESOURCE)

            withContext(Dispatchers.Main) {
                if (mResourceInProgressListener != null) {
                    mResourceInProgressListener!!.onComplete(getMissingMediaResult(resultAndError))
                }
                mResourceInProgressListener = null
            }

            if (resultAndError.data == AppInstallStatus.Installed) {
                RequestStats.markSuccess(InstallRequestSource.FOREGROUND_LAZY_RESOURCE)
            }
            getMissingMediaResult(resultAndError)
        } else {
            MissingMediaDownloadResult.InProgress
        }
    }

    // downloads the resource
    @Synchronized
    private fun recoverResource(platform: AndroidCommCarePlatform, it: Resource, source: InstallRequestSource): ResultAndError<AppInstallStatus> {
        resourceInProgress = it
        var result: ResultAndError<AppInstallStatus>
        result = try {
            if (!isResourceMissing(it)) {
                ResultAndError(AppInstallStatus.Installed)
            } else {
                platform.globalResourceTable.recoverResource(it, platform, ResourceInstallUtils.getProfileReference(), ResourceInstallContext(source))
                ResultAndError(AppInstallStatus.Installed)
            }
        } catch (e: Exception) {
            handleRecoverResourceFailure(e)
        } finally {
            resourceInProgress = null
        }
        return result
    }


    private fun getMissingMediaResult(result: ResultAndError<AppInstallStatus>): MissingMediaDownloadResult {
        return if (result.data == AppInstallStatus.Installed) {
            MissingMediaDownloadResult.Success
        } else {
            MissingMediaDownloadResult.Error(result.errorMessage)
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

    private fun startPinnedNotification() {
        if (CommCareApplication.notificationManager().areNotificationsEnabled()) {
            mPinnedNotificationProgress = PinnedNotificationWithProgress(CommCareApplication.instance(), "media.pinned.download",
                    "media.pinned.progress", R.drawable.update_download_icon)
        }
    }

    private fun updateNotification(complete: Int, total: Int) {
        if (this::mPinnedNotificationProgress.isInitialized) {
            mPinnedNotificationProgress.handleTaskUpdate(complete, total)
        }
    }

    private fun cancelNotification() {
        if (this::mPinnedNotificationProgress.isInitialized) {
            mPinnedNotificationProgress.handleTaskCancellation()
        }
    }
}
