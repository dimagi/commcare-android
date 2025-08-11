package org.commcare.android.database.connect.models

import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable

@Table(PersonalIdCredentialV15.STORAGE_KEY)
class PersonalIdCredentialV15 : Persisted(), Serializable {

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

    }
}
