package org.commcare.home

import android.widget.Toast
import org.commcare.activities.CommCareActivity
import org.commcare.tasks.DataPullTask.PullTaskResult

/**
 * What [SyncDelegate] needs from the activity it syncs for.
 */
interface SyncHost {
    /**
     * The activity sync tasks connect to as their task connector, which shows their progress
     * dialogs. Its `getReceiver()` must lead task results back to the delegate.
     */
    val syncTaskActivity: CommCareActivity<*>

    /** Show the outcome of a finished pull or form send. */
    fun updateUiAfterDataPullOrSend(
        message: String,
        success: Boolean,
    )

    /** A form send or pull finished. Hosts that need more than the message override this. */
    fun onSyncStepFinished(step: SyncStepResult) {
        updateUiAfterDataPullOrSend(step.message, step.success)
    }

    /** A sync the user asked for was not attempted, such as when offline; [message] says why. */
    fun onSyncNotAttempted(message: String) {
        displayToast(message)
    }

    /** A form send moved on; hosts that show its progress override this. */
    fun onFormSendProgress(progress: FormSendProgress.State) {}

    /** A pull finished and the delegate has reported it; the host may react further. */
    fun onPullTaskFinished(result: PullTaskResult)

    /** Show a sync message the user should see, such as why a sync was not attempted. */
    fun displayToast(message: String) {
        Toast.makeText(syncTaskActivity, message, Toast.LENGTH_LONG).show()
    }
}
