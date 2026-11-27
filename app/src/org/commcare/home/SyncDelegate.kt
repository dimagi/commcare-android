package org.commcare.home

import androidx.annotation.VisibleForTesting
import org.commcare.CommCareApplication
import org.commcare.activities.FormAndDataSyncer
import org.commcare.activities.UpdateActivity
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.preferences.HiddenPreferences
import org.commcare.sync.ProcessAndSendTask
import org.commcare.tasks.DataPullTask
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.commcare.tasks.PrimeEntityCacheHelper
import org.commcare.tasks.PullTaskResultReceiver
import org.commcare.tasks.ResultAndError
import org.commcare.utils.FormUploadResult
import org.commcare.views.dialogs.CustomProgressDialog
import org.commcare.views.dialogs.StandardAlertDialog
import org.commcare.views.notifications.NotificationActionButtonInfo
import org.commcare.views.notifications.NotificationMessageFactory
import org.javarosa.core.services.locale.Localization
import java.io.File

/**
 * Owns form sending and data pulls for a host activity, and reports their results.
 *
 * The host stays the task connector, so the in-activity progress dialogs keep working, while this
 * delegate is the receiver the tasks report to.
 */
class SyncDelegate(
    private val host: SyncHost,
) : PullTaskResultReceiver {
    private var formAndDataSyncer = FormAndDataSyncer()
    private var userLaunchedSync = false
    private var userTriggeredSync = false
    private var formSendProgress = FormSendProgress()

    /** The activity this delegate's tasks connect to. */
    val syncTaskActivity get() = host.syncTaskActivity

    /** Send unsent forms; a sync follows once they are sent, or straight away when there are none. */
    fun sendFormsOrSync(userTriggered: Boolean) {
        startUnsentFormsTask(syncAfterwards = true, userTriggered = userTriggered)
    }

    fun startUnsentFormsTask(
        syncAfterwards: Boolean,
        userTriggered: Boolean,
    ) {
        userLaunchedSync = userTriggered
        userTriggeredSync = userTriggered
        formSendProgress = FormSendProgress()
        formAndDataSyncer.startUnsentFormsTask(this, syncAfterwards, userTriggered)
    }

    /** Launch step: run the sync an app update left pending, or that a blocked update waits on. */
    fun runPostUpdateSyncIfNeeded(): Boolean {
        if (!CommCareApplication.instance().isPostUpdateSyncNeeded && !UpdateActivity.isUpdateBlockedOnSync()) {
            return false
        }
        HiddenPreferences.setPostUpdateSyncNeeded(false)
        sendFormsOrSync(false)
        return true
    }

    /** Launch step: run the sync FCM asked for while the user was logged out. */
    fun runPendingSyncRequestIfNeeded(): Boolean {
        val username =
            CommCareApplication
                .instance()
                .session.loggedInUser.username
        if (!HiddenPreferences.isPendingSyncRequest(username)) {
            return false
        }
        sendFormsOrSync(false)
        return true
    }

    /** Pull data for the logged-in user without sending forms first. */
    fun syncDataForLoggedInUser(userTriggered: Boolean) {
        userTriggeredSync = userTriggered
        formAndDataSyncer.syncDataForLoggedInUser(this, false, userTriggered)
    }

    fun performCustomRestoreFromFile(restoreFile: File) {
        formAndDataSyncer.performCustomRestoreFromFile(this, restoreFile)
    }

    @VisibleForTesting
    fun setFormAndDataSyncer(syncer: FormAndDataSyncer) {
        formAndDataSyncer = syncer
    }

    override fun handlePullTaskResult(
        resultAndError: ResultAndError<PullTaskResult>,
        userTriggeredSync: Boolean,
        formsToSend: Boolean,
        usingRemoteKeyManagement: Boolean,
    ) {
        if (!CommCareApplication.instance().isConsumerApp) {
            reportPullResult(resultAndError, userTriggeredSync, formsToSend)
        }
        host.onPullTaskFinished(resultAndError.data)
    }

    override fun handlePullTaskUpdate(vararg update: Int?) {
        val activity = syncTaskActivity
        when (update[0]) {
            DataPullTask.PROGRESS_STARTED -> {
                activity.updateProgress(Localization.get("sync.progress.purge"), DataPullTask.DATA_PULL_TASK_ID)
            }

            DataPullTask.PROGRESS_CLEANED -> {
                activity.updateProgress(Localization.get("sync.progress.authing"), DataPullTask.DATA_PULL_TASK_ID)
                activity.updateProgressBarVisibility(false)
            }

            DataPullTask.PROGRESS_AUTHED -> {
                activity.updateProgress(Localization.get("sync.progress.downloading"), DataPullTask.DATA_PULL_TASK_ID)
                activity.updateProgressBarVisibility(false)
            }

            DataPullTask.PROGRESS_DOWNLOADING -> {
                activity.updateProgress(
                    Localization.get("sync.process.downloading.progress", arrayOf(update[1].toString())),
                    Localization.get("sync.downloading.title"),
                    DataPullTask.DATA_PULL_TASK_ID,
                )
            }

            DataPullTask.PROGRESS_DOWNLOADING_COMPLETE -> {
                activity.hideTaskCancelButton()
            }

            DataPullTask.PROGRESS_PROCESSING -> {
                activity.updateProgress(
                    syncProgressMessage(update),
                    Localization.get("sync.processing.title"),
                    DataPullTask.DATA_PULL_TASK_ID,
                )
                activity.updateProgressBar(update[1]!!, update[2]!!, DataPullTask.DATA_PULL_TASK_ID)
            }

            DataPullTask.PROGRESS_RECOVERY_NEEDED -> {
                activity.updateProgress(Localization.get("sync.recover.needed"), DataPullTask.DATA_PULL_TASK_ID)
            }

            DataPullTask.PROGRESS_RECOVERY_STARTED -> {
                activity.updateProgress(Localization.get("sync.recover.started"), DataPullTask.DATA_PULL_TASK_ID)
            }

            DataPullTask.PROGRESS_SERVER_PROCESSING -> {
                activity.updateProgress(
                    syncProgressMessage(update),
                    Localization.get("sync.waiting.title"),
                    DataPullTask.DATA_PULL_TASK_ID,
                )
                activity.updateProgressBar(update[1]!!, update[2]!!, DataPullTask.DATA_PULL_TASK_ID)
            }
        }
    }

    override fun handlePullTaskError() {
        host.onSyncStepFinished(SyncStepResult(Localization.get("sync.fail.unknown"), FAIL, userTriggeredSync))
    }

    fun handleSyncNotAttempted(message: String) {
        host.onSyncNotAttempted(message)
    }

    /** A progress update from a form send. */
    fun handleFormSendUpdate(vararg update: Long?) {
        formSendProgress.onUpdate(update)?.let { host.onFormSendProgress(it) }
    }

    fun handleFormUploadResult(
        result: FormUploadResult,
        formLabel: String,
        userTriggered: Boolean,
        formsSent: Int = 0,
        pullFollows: Boolean = false,
    ) {
        val report = { message: String, success: Boolean ->
            updateUiForFormUploadResult(
                SyncStepResult(message, success, userTriggered, formsSent, pullFollows && success),
            )
        }
        when (result) {
            FormUploadResult.FULL_SUCCESS -> {
                report(formLabel, SUCCESS)
            }

            FormUploadResult.BAD_CERTIFICATE -> {
                reportBadCertificate()
                report(Localization.get(result.localeKeyBase), FAIL)
            }

            FormUploadResult.ACTIONABLE_FAILURE -> {
                report(result.errorMessage, FAIL)
            }

            FormUploadResult.RATE_LIMITED -> {
                showRateLimitError(userTriggered)
            }

            FormUploadResult.CANCELLED -> {
                report(Localization.get(result.localeKeyBase) + " " + formLabel, FAIL)
            }

            else -> {
                report(Localization.get(result.localeKeyBase), FAIL)
            }
        }
        PrimeEntityCacheHelper.scheduleEntityCacheInvalidation()
    }

    /** The progress dialog for a sync task, or null when [taskId] isn't one of them. */
    fun generateProgressDialog(taskId: Int): CustomProgressDialog? =
        when (taskId) {
            ProcessAndSendTask.SEND_PHASE_ID -> {
                CustomProgressDialog
                    .newInstance(
                        Localization.get("sync.progress.submitting.title"),
                        Localization.get("sync.progress.submitting"),
                        taskId,
                    ).apply { addCancelButton() }
            }

            ProcessAndSendTask.PROCESSING_PHASE_ID -> {
                CustomProgressDialog
                    .newInstance(
                        Localization.get("form.entry.processing.title"),
                        Localization.get("form.entry.processing"),
                        taskId,
                    ).apply {
                        addProgressBar()
                        addCancelButton()
                    }
            }

            DataPullTask.DATA_PULL_TASK_ID -> {
                CustomProgressDialog
                    .newInstance(
                        Localization.get("sync.communicating.title"),
                        Localization.get("sync.progress.purge"),
                        taskId,
                    ).apply {
                        if (userLaunchedSync) {
                            addCancelButton()
                        }
                        userLaunchedSync = false
                    }
            }

            else -> {
                null
            }
        }

    private fun reportPullResult(
        resultAndError: ResultAndError<PullTaskResult>,
        userTriggeredSync: Boolean,
        formsToSend: Boolean,
    ) {
        val result = resultAndError.data
        when (result) {
            PullTaskResult.DOWNLOAD_SUCCESS -> {
                CommCareApplication.notificationManager().clearNotifications(
                    NotificationMessageFactory.StockMessages.BadSslCertificate.category,
                )
                reportPullStep(Localization.get("sync.success.synced"), SUCCESS, userTriggeredSync)
            }

            PullTaskResult.ACTIONABLE_FAILURE -> {
                reportPullStep(resultAndError.errorMessage, FAIL, userTriggeredSync)
            }

            PullTaskResult.BAD_CERTIFICATE -> {
                reportBadCertificate()
                reportPullStep(Localization.get("sync.fail.badcert"), FAIL, userTriggeredSync)
            }

            else -> {
                failureMessageKey(result)?.let { reportPullStep(Localization.get(it), FAIL, userTriggeredSync) }
            }
        }

        val syncTrigger =
            if (userTriggeredSync) AnalyticsParamValue.SYNC_TRIGGER_USER else AnalyticsParamValue.SYNC_TRIGGER_AUTO
        val syncMode =
            if (formsToSend) AnalyticsParamValue.SYNC_MODE_SEND_FORMS else AnalyticsParamValue.SYNC_MODE_JUST_PULL_DATA
        FirebaseAnalyticsUtil.reportSyncResult(
            result == PullTaskResult.DOWNLOAD_SUCCESS,
            syncTrigger,
            syncMode,
            result.analyticsFailureReasonParam,
        )
    }

    private fun failureMessageKey(result: PullTaskResult): String? =
        when (result) {
            PullTaskResult.EMPTY_URL -> "sync.fail.empty.url"
            PullTaskResult.TOKEN_UNAVAILABLE -> "sync.fail.token.unavailable"
            PullTaskResult.TOKEN_DENIED -> "sync.fail.token.denied"
            PullTaskResult.AUTH_FAILED -> "sync.fail.auth.loggedin"
            PullTaskResult.BAD_DATA, PullTaskResult.BAD_DATA_REQUIRES_INTERVENTION -> "sync.fail.bad.data"
            PullTaskResult.SERVER_ERROR -> "sync.fail.server.error"
            PullTaskResult.RATE_LIMITED_SERVER_ERROR -> "sync.fail.rate.limited.server.error"
            PullTaskResult.UNREACHABLE_HOST -> "sync.fail.bad.network"
            PullTaskResult.CONNECTION_TIMEOUT -> "sync.fail.timeout"
            PullTaskResult.UNKNOWN_FAILURE -> "sync.fail.unknown"
            PullTaskResult.CANCELLED -> "sync.fail.cancelled"
            PullTaskResult.ENCRYPTION_FAILURE -> "sync.fail.encryption.failure"
            PullTaskResult.SESSION_EXPIRE -> "sync.fail.session.expire"
            PullTaskResult.RECOVERY_FAILURE -> "sync.fail.recovery.failure"
            PullTaskResult.AUTH_OVER_HTTP -> "auth.over.http"
            PullTaskResult.CAPTIVE_PORTAL -> "connection.captive_portal.action"
            else -> null
        }

    private fun reportBadCertificate() {
        CommCareApplication.notificationManager().reportNotificationMessage(
            NotificationMessageFactory.message(
                NotificationMessageFactory.StockMessages.BadSslCertificate,
                NotificationActionButtonInfo.ButtonAction.LAUNCH_DATE_SETTINGS,
            ),
        )
    }

    private fun reportPullStep(
        message: String,
        success: Boolean,
        userTriggered: Boolean,
    ) {
        host.onSyncStepFinished(SyncStepResult(message, success, userTriggered))
    }

    private fun updateUiForFormUploadResult(step: SyncStepResult) {
        host.onSyncStepFinished(step)
        if (step.success) {
            CommCareApplication.instance().session.initHeartbeatLifecycle()
        }
    }

    private fun showRateLimitError(userTriggered: Boolean) {
        if (HiddenPreferences.isRateLimitPopupDisabled() || !userTriggered) {
            updateUiForFormUploadResult(
                SyncStepResult(Localization.get(FormUploadResult.RATE_LIMITED.localeKeyBase), FAIL, userTriggered),
            )
            return
        }
        val dialog =
            StandardAlertDialog.getBasicAlertDialog(
                Localization.get("form.send.rate.limit.error.title"),
                Localization.get("form.send.rate.limit.error.message"),
                null,
            )
        dialog.setNegativeButton(Localization.get("rate.limit.error.dialog.do.not.show")) { shown, _ ->
            HiddenPreferences.disableRateLimitPopup(true)
            shown.dismiss()
        }
        dialog.setPositiveButton(Localization.get("rate.limit.error.dialog.close")) { shown, _ ->
            shown.dismiss()
        }
        syncTaskActivity.showAlertDialog(dialog)
    }

    private fun syncProgressMessage(update: Array<out Int?>): String {
        val numerator = update[1]!!
        val denominator = maxOf(numerator, update[2]!!)
        return Localization.get("sync.progress", arrayOf(numerator.toString(), denominator.toString()))
    }

    private companion object {
        const val SUCCESS = true
        const val FAIL = false
    }
}
