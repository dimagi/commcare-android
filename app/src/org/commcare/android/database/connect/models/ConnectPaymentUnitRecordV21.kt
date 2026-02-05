package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_AMOUNT
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_DAILY
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_NAME
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_TOTAL
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_UNIT_ID
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable

@Table(ConnectPaymentUnitRecordV21.STORAGE_KEY)
class ConnectPaymentUnitRecordV21 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_JOB_ID)
    var jobId = 0

    @Persisting(2)
    @MetaField(META_UNIT_ID)
    var unitId = 0

    @Persisting(3)
    @MetaField(META_NAME)
    var name: String? = null

    @Persisting(4)
    @MetaField(META_TOTAL)
    var maxTotal = 0

    @Persisting(5)
    @MetaField(META_DAILY)
    var maxDaily = 0

    @Persisting(6)
    @MetaField(META_AMOUNT)
    var amount = 0

    companion object {
        const val STORAGE_KEY = ConnectPaymentUnitRecord.STORAGE_KEY
    }
}
