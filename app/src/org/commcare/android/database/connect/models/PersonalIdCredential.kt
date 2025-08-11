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

    @Persisting(9)
    @MetaField(META_ISSUER_ENVIRONMENT)
    var issuerEnvironment: String = ""

    @Persisting(10)
    @MetaField(META_SLUG)
    var slug: String = ""

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
        const val META_ISSUER_ENVIRONMENT = "issuer_environment"
        const val META_SLUG = "slug"

        @JvmStatic
        fun fromJsonArray(jsonArray: JSONArray): List<PersonalIdCredential> {
            val validCredential = mutableListOf<PersonalIdCredential>()
            val corruptCredential = mutableListOf<String>()

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
                        issuerEnvironment = obj.getString(META_ISSUER_ENVIRONMENT)
                        slug = obj.getString(META_SLUG)
                    }
                    validCredential.add(credential)
                } catch (e: JSONException) {
                    Logger.exception("Corrupt credential at index $i", e)
                    corruptCredential.add("Index $i:\n${obj ?: "Unknown JSON"}\nError: ${e.message}")
                }
            }

            if (corruptCredential.isNotEmpty()) {
                val errorMessage = corruptCredential.joinToString(
                    separator = "\n\n",
                    prefix = "Found ${corruptCredential.size} corrupt credentials:\n"
                )
                Logger.log("CorruptCredentials", errorMessage)
                throw RuntimeException(errorMessage)
            }

            return validCredential
        }

        @JvmStatic
        fun fromV15(old: PersonalIdCredentialV15): PersonalIdCredential {
            val newRecord = PersonalIdCredential()
            newRecord.uuid = old.uuid
            newRecord.appId = old.appId
            newRecord.oppId = old.oppId
            newRecord.issuedDate = old.issuedDate
            newRecord.title = old.title
            newRecord.issuer = old.issuer
            newRecord.level = old.level
            newRecord.type = old.type
            return newRecord
        }
    }
}
