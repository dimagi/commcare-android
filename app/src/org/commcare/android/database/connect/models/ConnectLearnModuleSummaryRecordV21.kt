package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_DESCRIPTION
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_ESTIMATE
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_INDEX
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_NAME
import org.commcare.android.database.connect.models.ConnectLearnModuleSummaryRecord.META_SLUG
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(ConnectLearnModuleSummaryRecordV21.STORAGE_KEY)
class ConnectLearnModuleSummaryRecordV21 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_SLUG)
    private var slug: String? = null

    @Persisting(2)
    @MetaField(META_NAME)
    private var name: String? = null

    @Persisting(3)
    @MetaField(META_DESCRIPTION)
    private var description: String? = null

    @Persisting(4)
    @MetaField(META_ESTIMATE)
    private var timeEstimate = 0

    @Persisting(5)
    @MetaField(META_JOB_ID)
    private var jobId = 0

    @Persisting(6)
    @MetaField(META_INDEX)
    private var moduleIndex = 0

    @Persisting(7)
    private var lastUpdate: Date? = null

    companion object {
        const val STORAGE_KEY = ConnectLearnModuleSummaryRecord.STORAGE_KEY

        fun fromV21(connectLearnModuleSummaryRecordV21: ConnectLearnModuleSummaryRecordV21): ConnectLearnModuleSummaryRecord {
            val learnModuleSummaryRecord = ConnectLearnModuleSummaryRecord()
            learnModuleSummaryRecord.moduleIndex = connectLearnModuleSummaryRecordV21.moduleIndex
            learnModuleSummaryRecord.slug = connectLearnModuleSummaryRecordV21.slug
            learnModuleSummaryRecord.name = connectLearnModuleSummaryRecordV21.name
            learnModuleSummaryRecord.description = connectLearnModuleSummaryRecordV21.description
            learnModuleSummaryRecord.timeEstimate = connectLearnModuleSummaryRecordV21.timeEstimate
            learnModuleSummaryRecord.lastUpdate = connectLearnModuleSummaryRecordV21.lastUpdate
            learnModuleSummaryRecord.jobId = connectLearnModuleSummaryRecordV21.jobId
            learnModuleSummaryRecord.jobUUID = "${connectLearnModuleSummaryRecordV21.jobId}"
            return learnModuleSummaryRecord
        }
    }
}
