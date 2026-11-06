package org.commcare.home

import android.app.Activity.RESULT_OK
import android.content.Intent
import androidx.lifecycle.Lifecycle
import org.commcare.CommCareApplication
import org.commcare.activities.DispatchActivity
import org.commcare.activities.UpdateActivity
import org.commcare.connect.database.ConnectTaskUtils
import org.commcare.preferences.HiddenPreferences
import org.commcare.recovery.measures.RecoveryMeasuresHelper
import org.commcare.sync.FirebaseMessagingDataSyncer
import org.commcare.tasks.DataPullTask.PullTaskResult
import org.commcare.util.LogTypes
import org.commcare.utils.CommCareUtil
import org.commcare.utils.SessionUnavailableException
import org.javarosa.core.services.Logger
import java.text.SimpleDateFormat

/**
 * The app-health checks home runs while the user is on it: recovery measures, the syncs the app
 * owes, the update prompt and forced log submission, plus resuming an update that waited on a sync.
 */
class AppHealthDelegate(
    private val host: HomeActivityHost,
    private val sync: SyncDelegate,
    private val appUpdate: AppUpdateDelegate,
) {
    private val dataSyncer by lazy { FirebaseMessagingDataSyncer(host.hostContext) }

    /** Finish home if the session is gone; otherwise run the first pending health action, or show home. */
    fun showHomeOrPendingAction() {
        try {
            CommCareApplication.instance().session
        } catch (e: SessionUnavailableException) {
            host.finishWithResult(RESULT_OK)
            return
        }
        if (!runChecks()) {
            host.refreshHostUi()
        }
    }

    /**
     * Run the checks unless they already ran in this session.
     *
     * @return true when an action took over the foreground
     */
    fun runChecksIfNotYetRun(): Boolean = !CommCareApplication.instance().session.appHealthChecksCompleted() && runChecks()

    /** Run the data sync FCM asked for while home was in the background. */
    fun runPendingSyncRequest() {
        val username = loggedInUsername()
        if (HiddenPreferences.isPendingSyncRequest(username)) {
            dataSyncer.syncData(HiddenPreferences.getPendingSyncRequest(username))
        }
    }

    /** A pull finished; resume an app update that was waiting on it. */
    fun onPullTaskFinished(result: PullTaskResult) {
        if (!UpdateActivity.sBlockedUpdateWorkflowInProgress) {
            return
        }
        val intent = Intent(host.hostContext.applicationContext, UpdateActivity::class.java)
        intent.putExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, true)
        intent.putExtra(UpdateActivity.KEY_PRE_UPDATE_SYNC_SUCCEED, result == PullTaskResult.DOWNLOAD_SUCCESS)
        host.startActivity(intent)
    }

    /**
     * Run the first pending app-health action: recovery measures, a sync a blocked update waits
     * on, a pending sync, then an update prompt. Forced log submission runs regardless.
     *
     * @return true when an action took over the foreground
     */
    private fun runChecks(): Boolean {
        val kickedOff =
            when {
                RecoveryMeasuresHelper.recoveryMeasuresPending() -> {
                    host.finishWithResult(
                        RESULT_OK,
                        Intent().putExtra(DispatchActivity.EXECUTE_RECOVERY_MEASURES, true),
                    )
                    true
                }

                UpdateActivity.isUpdateBlockedOnSync() && UpdateActivity.sBlockedUpdateWorkflowInProgress -> {
                    triggerAutoSync()
                    true
                }

                CommCareApplication.instance().isSyncPending ||
                    ConnectTaskUtils.isLastTaskUpdateLaterThanLastSync(host.hostContext) -> {
                    triggerAutoSync()
                    true
                }

                else -> {
                    appUpdate.promptForUpdateIfNeeded(true)
                }
            }
        val session = CommCareApplication.instance().session
        if (HiddenPreferences.shouldForceLogs(session.loggedInUser.uniqueId)) {
            CommCareUtil.triggerLogSubmission(CommCareApplication.instance(), true)
        }
        session.setAppHealthChecksCompleted()
        return kickedOff
    }

    /** Send forms, then sync, logging when the last attempt was. */
    private fun triggerAutoSync() {
        val lastUploadSyncAttempt = HiddenPreferences.getLastUploadSyncAttempt()
        val footer =
            if (lastUploadSyncAttempt == 0L) "never" else SimpleDateFormat.getDateTimeInstance().format(lastUploadSyncAttempt)
        Logger.log(LogTypes.TYPE_USER, "autosync triggered. Last Sync|$footer")
        if (host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            host.refreshHostUi()
        }
        sync.sendFormsOrSync(false)
    }

    private fun loggedInUsername(): String =
        CommCareApplication
            .instance()
            .session.loggedInUser.username
}
