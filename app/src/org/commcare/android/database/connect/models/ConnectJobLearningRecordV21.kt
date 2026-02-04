package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectJobLearningRecord.META_DATE
import org.commcare.android.database.connect.models.ConnectJobLearningRecord.META_DURATION
import org.commcare.android.database.connect.models.ConnectJobLearningRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectJobLearningRecord.META_MODULE
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(ConnectJobLearningRecordV21.STORAGE_KEY)
class ConnectJobLearningRecordV21 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_JOB_ID)
    private var jobId = 0

    @Persisting(2)
    @MetaField(META_DATE)
    private var date: Date? = null

    @Persisting(3)
    @MetaField(META_MODULE)
    private var moduleId = 0

    @Persisting(4)
    @MetaField(META_DURATION)
    private var duration: String? = null

    @Persisting(5)
    private var lastUpdate: Date? = null

    companion object {
        const val STORAGE_KEY = ConnectJobLearningRecord.STORAGE_KEY

        fun fromV21(connectJobLearningRecordV21: ConnectJobLearningRecordV21): ConnectJobLearningRecord {
            val record = ConnectJobLearningRecord()
            record.jobId = connectJobLearningRecordV21.jobId
            record.date = connectJobLearningRecordV21.date
            record.moduleId = connectJobLearningRecordV21.moduleId
            record.duration = connectJobLearningRecordV21.duration
            record.lastUpdate = connectJobLearningRecordV21.lastUpdate
            record.jobUUID = "${connectJobLearningRecordV21.jobId}"
            return record
        }
    }
}
