package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_DESCRIPTION
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_ESTIMATE
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_INDEX
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_JOB_UUID
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_NAME
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_SLUG
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

/** Shape of [ConnectLearnModuleSummaryRecord] before the server module id was persisted. */
@Table(ConnectLearnModuleSummaryRecordV28.STORAGE_KEY)
class ConnectLearnModuleSummaryRecordV28 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_SLUG)
    var slug: String? = null

    @Persisting(2)
    @MetaField(META_NAME)
    var name: String? = null

    @Persisting(3)
    @MetaField(META_DESCRIPTION)
    var description: String? = null

    @Persisting(4)
    @MetaField(META_ESTIMATE)
    var timeEstimate = 0

    @Persisting(5)
    @MetaField(META_JOB_ID)
    var jobId = 0

    @Persisting(6)
    @MetaField(META_INDEX)
    var moduleIndex = 0

    @Persisting(7)
    var lastUpdate: Date? = null

    @Persisting(8)
    @MetaField(META_JOB_UUID)
    var jobUUID: String? = null

    companion object {
        const val STORAGE_KEY = ConnectLearnModuleSummaryRecord.STORAGE_KEY

        fun fromV21(connectLearnModuleSummaryRecordV21: ConnectLearnModuleSummaryRecordV21): ConnectLearnModuleSummaryRecordV28 {
            val connectLearnModuleSummaryRecordV28 = ConnectLearnModuleSummaryRecordV28()
            connectLearnModuleSummaryRecordV28.moduleIndex =
                connectLearnModuleSummaryRecordV21.moduleIndex
            connectLearnModuleSummaryRecordV28.slug = connectLearnModuleSummaryRecordV21.slug
            connectLearnModuleSummaryRecordV28.name = connectLearnModuleSummaryRecordV21.name
            connectLearnModuleSummaryRecordV28.description =
                connectLearnModuleSummaryRecordV21.description
            connectLearnModuleSummaryRecordV28.timeEstimate =
                connectLearnModuleSummaryRecordV21.timeEstimate
            connectLearnModuleSummaryRecordV28.lastUpdate =
                connectLearnModuleSummaryRecordV21.lastUpdate
            connectLearnModuleSummaryRecordV28.jobId = connectLearnModuleSummaryRecordV21.jobId
            connectLearnModuleSummaryRecordV28.jobUUID =
                connectLearnModuleSummaryRecordV21.jobId.toString()
            return connectLearnModuleSummaryRecordV28
        }
    }
}
