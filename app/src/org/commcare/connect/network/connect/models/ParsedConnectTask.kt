package org.commcare.connect.network.connect.models

import org.javarosa.core.model.utils.DateUtils
import org.json.JSONObject
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
) {
    companion object {
        fun fromJson(json: JSONObject): ParsedConnectTask {
            val assigned = json.getString("status") == "assigned"
            var dateModified: Date? = null

            if (json.has("date_modified")) {
                dateModified = DateUtils.parseDate(json.getString("date_modified"))
            }

            return ParsedConnectTask(assigned, dateModified)
        }
    }
}
