package org.commcare.home

import android.widget.Toast
import org.commcare.activities.CommCareActivity
import org.commcare.tasks.DataPullTask.PullTaskResult

/**
 * What [SyncDelegate] needs from the activity it syncs for.
 */
interface SyncHost {
    /**
     * The activity sync tasks connect to, which shows their progress dialogs. Its `getReceiver()`
     * must lead task results back to the delegate.
     */
    val syncConnector: CommCareActivity<*>

    /** Show the outcome of a finished pull or form send. */
    fun updateUiAfterDataPullOrSend(
        message: String,
        success: Boolean,
    )

    /** A pull finished and the delegate has reported it; the host may react further. */
    fun onPullTaskFinished(result: PullTaskResult)

    /** Show a sync message the user should see, such as why a sync was not attempted. */
    fun displayToast(message: String) {
        Toast.makeText(syncConnector, message, Toast.LENGTH_LONG).show()
    }
}
