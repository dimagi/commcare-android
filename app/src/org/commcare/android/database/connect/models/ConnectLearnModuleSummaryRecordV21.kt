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

    companion object {
        const val STORAGE_KEY = ConnectLearnModuleSummaryRecord.STORAGE_KEY
    }
}
