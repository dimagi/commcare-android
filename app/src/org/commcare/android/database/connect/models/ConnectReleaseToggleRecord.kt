package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.javarosa.core.model.utils.DateUtils
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.util.Date

@Table(ConnectReleaseToggleRecord.STORAGE_KEY)
class ConnectReleaseToggleRecord :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_SLUG)
    var slug: String
        private set

    @Persisting(2)
    @MetaField(META_ACTIVE)
    var active: Boolean
        private set

    @Persisting(3)
    @MetaField(META_CREATED_AT)
    var createdAt: Date
        private set

    @Persisting(4)
    @MetaField(META_MODIFIED_AT)
    var modifiedAt: Date
        private set

    init {
        slug = ""
        active = false
        createdAt = Date()
        modifiedAt = Date()
    }

    companion object {
        // Name of database that stores connect release toggles.
        const val STORAGE_KEY = "connect_release_toggles"

        const val META_SLUG = "slug"
        const val META_ACTIVE = "active"
        const val META_CREATED_AT = "created_at"
        const val META_MODIFIED_AT = "modified_at"

        @Throws(JSONException::class)
        fun fromJson(json: JSONObject): List<ConnectReleaseToggleRecord> {
            val releaseToggles = mutableListOf<ConnectReleaseToggleRecord>()
            val slugKeys = json.keys()

            while (slugKeys.hasNext()) {
                val slugKey = slugKeys.next()
                val releaseToggleJson = json.getJSONObject(slugKey)

                val releaseToggle = ConnectReleaseToggleRecord().apply {
                    val createdAtDateString = releaseToggleJson.getString(META_CREATED_AT)
                    val modifiedAtDateString = releaseToggleJson.getString(META_MODIFIED_AT)

                    slug = slugKey
                    active = releaseToggleJson.getBoolean(META_ACTIVE)
                    createdAt = DateUtils.parseDateTime(createdAtDateString)
                    modifiedAt = DateUtils.parseDateTime(modifiedAtDateString)
                }

                releaseToggles.add(releaseToggle)
            }

            return releaseToggles
        }
    }
}
