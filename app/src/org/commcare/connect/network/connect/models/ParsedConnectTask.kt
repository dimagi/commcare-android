package org.commcare.connect.network.connect.models

import java.util.Date

/**
 * Transient task record built by DeliveryAppProgressResponseParser. Not persisted — only used to
 * hand critical values to ConnectJobRecord.syncRelearnTasksPrefs().
 *
 * @property assigned True if the task has been assigned but not yet completed, and false otherwise.
 * @property dateModified The date the task was last modified, which is an optional field from Server.
 */
data class ParsedConnectTask(
    val assigned: Boolean,
    val dateModified: Date?,
)
