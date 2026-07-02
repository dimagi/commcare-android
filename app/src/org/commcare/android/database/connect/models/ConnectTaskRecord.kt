package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.commcare.utils.hasNonNull
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
    var description: String? = null

    @Persisting(5)
    @MetaField(META_STATUS)
    var status: String = ""

    @Persisting(6)
    @MetaField(META_CONNECT_CHANNEL_ID)
    var connectChannelId: String? = null

    @Persisting(7)
    @MetaField(META_TYPE)
    var type: String = ""

    @Persisting(8)
    @MetaField(META_DUE_DATE)
    var dueDate: Date? = null

    @Persisting(9)
    @MetaField(META_DATE_CREATED)
    var dateCreated: Date = Date()

    @Persisting(10)
    @MetaField(META_DATE_MODIFIED)
    var dateModified: Date = Date()

    fun copyMutableFieldsFrom(incoming: ConnectTaskRecord) {
        name = incoming.name
        description = incoming.description
        status = incoming.status
        connectChannelId = incoming.connectChannelId
        type = incoming.type
        dueDate = incoming.dueDate
    }

    fun mutableFieldsDiffer(other: ConnectTaskRecord): Boolean =
        name != other.name ||
            description != other.description ||
            status != other.status ||
            connectChannelId != other.connectChannelId ||
            type != other.type ||
            dueDate != other.dueDate

    companion object {
        const val STORAGE_KEY = "connect_tasks"
        const val META_JOB_UUID = "opportunity_id"
        const val META_TASK_ID = "task_id"
        const val META_NAME = "name"
        const val META_DESCRIPTION = "description"
        const val META_STATUS = "status"
        const val META_CONNECT_CHANNEL_ID = "connect_channel_id"
        const val META_TYPE = "type"
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
            task.taskId = json.getString(META_TASK_ID)
            task.name = json.getString(META_NAME)
            task.description = json.optString(META_DESCRIPTION, "")
            task.status = json.getString(META_STATUS)
            task.connectChannelId = json.optString(META_CONNECT_CHANNEL_ID, null)
            task.type = json.getString(META_TYPE)
            if (json.hasNonNull(META_DUE_DATE)) {
                task.dueDate = DateUtils.parseDate(json.getString(META_DUE_DATE))
            }
            return task
        }
    }
}
