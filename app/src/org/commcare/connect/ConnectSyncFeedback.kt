package org.commcare.connect

import android.content.Context
import android.os.Bundle
import org.commcare.dalvik.R
import org.commcare.home.FormSendProgress
import org.commcare.home.SyncStepResult
import org.commcare.sync.ProcessAndSendTask
import org.commcare.tasks.DataPullTask
import org.commcare.views.connect.ConnectSyncProgressDialog
import org.javarosa.core.services.locale.Localization

/**
 * Decides what Opportunity Home shows while its app syncs and once the sync ends: the progress
 * dialog for each sync task, and the result a finished step leads to.
 */
class ConnectSyncFeedback {
    /** What a finished sync step leaves on screen. */
    sealed interface Outcome {
        /** A pull follows, so the sync isn't over yet. */
        data object Pending : Outcome

        /** A sync the user didn't start; its message is shown as a toast, as before. */
        data class Toast(
            val message: String,
        ) : Outcome

        data class Complete(
            val formsSent: Int,
        ) : Outcome

        /** Synced with no forms to send. */
        data object UpToDate : Outcome

        data class Failed(
            val details: String,
        ) : Outcome
    }

    /** The user hid this sync's progress, so its later tasks don't bring it back. */
    var progressHidden = false
        private set
    private var formsSent = 0

    fun isSyncTask(taskId: Int): Boolean = taskId in SYNC_TASK_IDS

    /** The progress dialog for [taskId], or null when it isn't a sync task or the user hid the sync. */
    fun progressDialogFor(
        context: Context,
        taskId: Int,
    ): ConnectSyncProgressDialog? {
        if (!isSyncTask(taskId) || progressHidden) {
            return null
        }
        val communicating = context.getString(R.string.connect_app_launch_dialog_syncing_message)
        val inProgress = context.getString(R.string.connect_sync_dialog_in_progress_title)
        return when (taskId) {
            ProcessAndSendTask.PROCESSING_PHASE_ID -> {
                ConnectSyncProgressDialog.newInstance(
                    context.getString(R.string.connect_sync_dialog_waiting_title),
                    communicating,
                    taskId,
                    dismissEnabled = true,
                )
            }

            ProcessAndSendTask.SEND_PHASE_ID -> {
                ConnectSyncProgressDialog.newInstance(inProgress, communicating, taskId, dismissEnabled = false)
            }

            else -> {
                ConnectSyncProgressDialog.newInstance(
                    inProgress,
                    Localization.get("sync.progress.purge"),
                    taskId,
                    dismissEnabled = true,
                )
            }
        }
    }

    /** The line a progress dialog shows for a form send at [progress]. */
    fun formProgressMessage(
        context: Context,
        progress: FormSendProgress.State,
    ): String = context.getString(R.string.connect_sync_dialog_forms_progress, progress.sent, progress.total)

    /** A user starts a sync: its progress shows again, and no earlier step's forms count toward it. */
    fun onSyncStarted() {
        progressHidden = false
        formsSent = 0
    }

    fun onProgressHidden() {
        progressHidden = true
    }

    fun onStepFinished(step: SyncStepResult): Outcome {
        if (step.pullFollows) {
            formsSent = step.formsSent
            return if (step.userTriggered) Outcome.Pending else Outcome.Toast(step.message)
        }
        val sent = formsSent + step.formsSent
        formsSent = 0
        progressHidden = false
        return when {
            !step.userTriggered -> Outcome.Toast(step.message)
            !step.success -> Outcome.Failed(step.message)
            sent > 0 -> Outcome.Complete(sent)
            else -> Outcome.UpToDate
        }
    }

    /** A sync the user asked for didn't start, such as when offline. */
    fun onSyncNotAttempted(message: String): Outcome {
        progressHidden = false
        formsSent = 0
        return Outcome.Failed(message)
    }

    fun saveState(outState: Bundle) {
        outState.putBoolean(KEY_PROGRESS_HIDDEN, progressHidden)
        outState.putInt(KEY_FORMS_SENT, formsSent)
    }

    fun restoreState(savedState: Bundle?) {
        if (savedState == null) {
            return
        }
        progressHidden = savedState.getBoolean(KEY_PROGRESS_HIDDEN)
        formsSent = savedState.getInt(KEY_FORMS_SENT)
    }

    private companion object {
        val SYNC_TASK_IDS =
            setOf(ProcessAndSendTask.PROCESSING_PHASE_ID, ProcessAndSendTask.SEND_PHASE_ID, DataPullTask.DATA_PULL_TASK_ID)
        const val KEY_PROGRESS_HIDDEN = "connect_sync_progress_hidden"
        const val KEY_FORMS_SENT = "connect_sync_forms_sent"
    }
}
