package org.commcare.connect.network.connect.models

import java.util.Date

/**
 * Transient task record built by DeliveryAppProgressResponseParser. Not
 * persisted — used only to hand task status + dateModified to
 * ConnectJobRecord.syncRelearnTasksPrefs.
 */
data class ParsedConnectTask(
    val status: String,
    val dateModified: Date?,
)

object ConnectTaskStatus {
    const val ASSIGNED = "assigned"
    const val COMPLETED = "completed"
}
