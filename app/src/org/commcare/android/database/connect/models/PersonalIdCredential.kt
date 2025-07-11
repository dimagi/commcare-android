package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import org.javarosa.core.services.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable

@Table(PersonalIdCredential.STORAGE_KEY)
class PersonalIdCredential : Persisted(), Serializable {

    @Persisting(1)
    @MetaField(META_UUID)
    var uuid: String = ""

    @Persisting(2)
    @MetaField(META_APP_ID)
    var appId: String = ""

    @Persisting(3)
    @MetaField(META_OPP_ID)
    var oppId: String = ""

    @Persisting(4)
    @MetaField(META_ISSUED_DATE)
    var issuedDate: String = ""

    @Persisting(5)
    @MetaField(META_TITLE)
    var title: String = ""

    @Persisting(6)
    @MetaField(META_ISSUER)
    var issuer: String = ""

    @Persisting(7)
    @MetaField(META_LEVEL)
    var level: String = ""

    @Persisting(8)
    @MetaField(META_TYPE)
    var type: String = ""

    companion object {
        const val STORAGE_KEY = "credential"

        const val META_UUID = "uuid"
        const val META_APP_ID = "app_id"
        const val META_OPP_ID = "opp_id"
        const val META_ISSUED_DATE = "date"
        const val META_TITLE = "title"
        const val META_ISSUER = "issuer"
        const val META_LEVEL = "level"
        const val META_TYPE = "type"

        @JvmStatic
        fun fromJsonArray(jsonArray: JSONArray): PersonalIdValidAndCorruptCredential {
            val valid = mutableListOf<PersonalIdCredential>()
            val corrupt = mutableListOf<PersonalIdCredential>()

            for (i in 0 until jsonArray.length()) {
                var obj: JSONObject? = null
                try {
                    obj = jsonArray.getJSONObject(i)

                    val credential = PersonalIdCredential().apply {
                        uuid = obj.getString(META_UUID)
                        appId = obj.getString(META_APP_ID)
                        oppId = obj.getString(META_OPP_ID)
                        issuedDate = obj.getString(META_ISSUED_DATE)
                        title = obj.getString(META_TITLE)
                        issuer = obj.getString(META_ISSUER)
                        level = obj.getString(META_LEVEL)
                        type = obj.getString(META_TYPE)

                        CredentialType.from(type)
                    }
                    valid.add(credential)
                } catch (e: JSONException) {
                    Logger.exception("Corrupt credential at index $i", e)
                    obj?.let { corrupt.add(corruptCredentialFromJson(it)) }
                }
            }

            return PersonalIdValidAndCorruptCredential(valid, corrupt)
        }

        @JvmStatic
        fun corruptCredentialFromJson(json: JSONObject): PersonalIdCredential {
            return PersonalIdCredential().apply {
                uuid = json.optString(META_UUID, "")
                appId = json.optString(META_APP_ID, "")
                oppId = json.optString(META_OPP_ID, "")
                issuedDate = json.optString(META_ISSUED_DATE, "")
                title = json.optString(META_TITLE, "")
                issuer = json.optString(META_ISSUER, "")
                level = json.optString(META_LEVEL, "")
                type = json.optString(META_TYPE, "")
            }
        }
    }

    fun getCredentialType(): CredentialType {
        return CredentialType.from(type)
    }
}
