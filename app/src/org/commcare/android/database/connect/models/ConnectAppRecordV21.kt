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
    private var jobId = 0

    @Persisting(2)
    private var isLearning = false

    @Persisting(3)
    @MetaField(META_DOMAIN)
    private var domain: String? = null

    @Persisting(4)
    @MetaField(META_APP_ID)
    private var appId: String? = null

    @Persisting(5)
    @MetaField(META_NAME)
    private var name: String? = null

    @Persisting(6)
    @MetaField(META_DESCRIPTION)
    private var description: String? = null

    @Persisting(7)
    @MetaField(META_ORGANIZATION)
    private var organization: String? = null

    @Persisting(8)
    @MetaField(META_PASSING_SCORE)
    private var passingScore = 0

    @Persisting(9)
    @MetaField(META_INSTALL_URL)
    private var installUrl: String? = null

    @Persisting(10)
    private var lastUpdate: Date? = null

    companion object {
        const val STORAGE_KEY = ConnectAppRecord.STORAGE_KEY

        fun fromV21(connectAppRecordV21: ConnectAppRecordV21): ConnectAppRecord {
            val app = ConnectAppRecord()
            app.jobId = connectAppRecordV21.jobId
            app.isLearning = connectAppRecordV21.isLearning
            app.domain = connectAppRecordV21.domain
            app.appId = connectAppRecordV21.appId
            app.jobUUID = "${connectAppRecordV21.jobId}"
            app.name = connectAppRecordV21.name
            app.description = connectAppRecordV21.description
            app.organization = connectAppRecordV21.organization
            app.passingScore = connectAppRecordV21.passingScore
            app.installUrl = connectAppRecordV21.installUrl
            return app
        }
    }
}
