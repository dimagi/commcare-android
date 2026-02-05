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
    var jobId = 0

    @Persisting(2)
    @MetaField(META_DATE)
    var date: Date? = null

    @Persisting(3)
    @MetaField(META_MODULE)
    var moduleId = 0

    @Persisting(4)
    @MetaField(META_DURATION)
    var duration: String? = null

    @Persisting(5)
    var lastUpdate: Date? = null

    companion object {
        const val STORAGE_KEY = ConnectJobLearningRecord.STORAGE_KEY
    }
}
