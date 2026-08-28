package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.commcare.utils.hasNonNull
import org.commcare.utils.optStringSafe
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.util.Date

@Table(ConnectTaskRecord.STORAGE_KEY)
class ConnectTaskRecord :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_JOB_UUID)
    var jobUUID: String = ""

    @Persisting(2)
    @MetaField(META_TASK_ID)
    var taskId: String = ""

    @Persisting(3)
    @MetaField(META_NAME)
    var name: String = ""

    @Persisting(4)
    @MetaField(META_DESCRIPTION)
    var description: String = ""

    @Persisting(5)
    @MetaField(META_STATUS)
    var status: String = ""

    @Persisting(6)
    @MetaField(META_CONNECT_CHANNEL_ID)
    var connectChannelId: String = ""

    @Persisting(7)
    @MetaField(META_MODE)
    var mode: String = ""

    @Persisting(8)
    @MetaField(META_DUE_DATE)
    var dueDate: Date? = null

    @Persisting(9)
    @MetaField(META_DATE_CREATED)
    var dateCreated: Date = Date()

    @Persisting(10)
    @MetaField(META_DATE_MODIFIED)
    var dateModified: Date = Date()

    val isOCSConversation: Boolean
        get() = mode == MODE_OCS

    companion object {
        const val STORAGE_KEY = "connect_tasks"

        const val STATUS_ASSIGNED = "assigned"
        const val STATUS_COMPLETED = "completed"
        const val MODE_OCS = "ocs"

        const val META_JOB_UUID = "opportunity_id"
        const val META_TASK_ID = "task_id"
        const val META_NAME = "name"
        const val META_DESCRIPTION = "description"
        const val META_STATUS = "status"
        const val META_CONNECT_CHANNEL_ID = "connect_channel_id"
        const val META_MODE = "mode"
        const val META_DUE_DATE = "due_date"
        const val META_DATE_CREATED = "date_created"
        const val META_DATE_MODIFIED = "date_modified"

        @JvmStatic
        @Throws(JSONException::class)
        fun fromJson(
            json: JSONObject,
            job: ConnectJobRecord,
        ): ConnectTaskRecord {
            val task = ConnectTaskRecord()
            task.jobUUID = job.jobUUID
            task.taskId = json.getString("assigned_task_id")
            task.name = json.getString("task_name")
            task.description = json.optStringSafe("task_description", "").orEmpty()
            task.status = json.getString("status")
            task.connectChannelId = json.optStringSafe("connect_channel_id", "").orEmpty()
            task.mode = json.optStringSafe("task_mode", "").orEmpty()
            if (json.hasNonNull("due_date")) {
                task.dueDate = DateUtils.parseDate(json.getString("due_date"))
            }
            if (json.hasNonNull("date_created")) {
                task.dateCreated = DateUtils.parseDateTime(json.getString("date_created")) ?: Date()
            }
            if (json.hasNonNull("date_modified")) {
                task.dateModified = DateUtils.parseDateTime(json.getString("date_modified")) ?: Date()
            }
            return task
        }
    }
}
