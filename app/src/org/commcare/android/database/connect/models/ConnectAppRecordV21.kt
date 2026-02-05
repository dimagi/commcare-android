package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectAppRecord.META_APP_ID
import org.commcare.android.database.connect.models.ConnectAppRecord.META_DESCRIPTION
import org.commcare.android.database.connect.models.ConnectAppRecord.META_DOMAIN
import org.commcare.android.database.connect.models.ConnectAppRecord.META_INSTALL_URL
import org.commcare.android.database.connect.models.ConnectAppRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectAppRecord.META_NAME
import org.commcare.android.database.connect.models.ConnectAppRecord.META_ORGANIZATION
import org.commcare.android.database.connect.models.ConnectAppRecord.META_PASSING_SCORE
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(ConnectAppRecordV21.STORAGE_KEY)
class ConnectAppRecordV21 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_JOB_ID)
    var jobId = 0

    @Persisting(2)
    var isLearning = false

    @Persisting(3)
    @MetaField(META_DOMAIN)
    var domain: String? = null

    @Persisting(4)
    @MetaField(META_APP_ID)
    var appId: String? = null

    @Persisting(5)
    @MetaField(META_NAME)
    var name: String? = null

    @Persisting(6)
    @MetaField(META_DESCRIPTION)
    var description: String? = null

    @Persisting(7)
    @MetaField(META_ORGANIZATION)
    var organization: String? = null

    @Persisting(8)
    @MetaField(META_PASSING_SCORE)
    var passingScore = 0

    @Persisting(9)
    @MetaField(META_INSTALL_URL)
    var installUrl: String? = null

    @Persisting(10)
    var lastUpdate: Date? = null

    companion object {
        const val STORAGE_KEY = ConnectAppRecord.STORAGE_KEY
    }
}
