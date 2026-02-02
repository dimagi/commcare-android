package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_AMOUNT
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_DAILY
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_NAME
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_TOTAL
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord.META_UNIT_ID
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.models.MetaField
import org.commcare.modern.database.Table
import java.io.Serializable

@Table(ConnectPaymentUnitRecordV21.STORAGE_KEY)
class ConnectPaymentUnitRecordV21 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_JOB_ID)
    private var jobId = 0

    @Persisting(2)
    @MetaField(META_UNIT_ID)
    private var unitId = 0

    @Persisting(3)
    @MetaField(META_NAME)
    private var name: String? = null

    @Persisting(4)
    @MetaField(META_TOTAL)
    private var maxTotal = 0

    @Persisting(5)
    @MetaField(META_DAILY)
    private var maxDaily = 0

    @Persisting(6)
    @MetaField(META_AMOUNT)
    private var amount = 0

    companion object {
        const val STORAGE_KEY = ConnectPaymentUnitRecord.STORAGE_KEY

        fun fromV21(connectPaymentUnitRecordV21: ConnectPaymentUnitRecordV21): ConnectPaymentUnitRecord {
            val paymentUnit = ConnectPaymentUnitRecord()
            paymentUnit.jobId = connectPaymentUnitRecordV21.jobId
            paymentUnit.jobUUID = "${connectPaymentUnitRecordV21.jobId}"
            paymentUnit.unitId = connectPaymentUnitRecordV21.unitId
            paymentUnit.unitUUID = "${connectPaymentUnitRecordV21.unitId}"
            paymentUnit.name = connectPaymentUnitRecordV21.name
            paymentUnit.maxTotal = connectPaymentUnitRecordV21.maxTotal
            paymentUnit.maxDaily = connectPaymentUnitRecordV21.maxDaily
            paymentUnit.amount = connectPaymentUnitRecordV21.amount
            return paymentUnit
        }
    }
}
