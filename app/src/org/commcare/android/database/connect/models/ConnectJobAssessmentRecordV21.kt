package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord.META_DATE
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord.META_PASSED
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord.META_PASSING_SCORE
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord.META_SCORE
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(ConnectJobAssessmentRecordV21.STORAGE_KEY)
class ConnectJobAssessmentRecordV21 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_JOB_ID)
    var jobId = 0

    @Persisting(2)
    @MetaField(META_DATE)
    var date: Date? = null

    @Persisting(3)
    @MetaField(META_SCORE)
    var score = 0

    @Persisting(4)
    @MetaField(META_PASSING_SCORE)
    var passingScore = 0

    @Persisting(5)
    @MetaField(META_PASSED)
    var passed = false

    @Persisting(6)
    var lastUpdate: Date? = null

    companion object {
        const val STORAGE_KEY = ConnectJobAssessmentRecord.STORAGE_KEY
    }
}
