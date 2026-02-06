package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord.META_AMOUNT
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord.META_CONFIRMED
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord.META_CONFIRMED_DATE
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord.META_DATE
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord.META_PAYMENT_ID
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(ConnectJobPaymentRecordV21.STORAGE_KEY)
class ConnectJobPaymentRecordV21 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_JOB_ID)
    var jobId = 0

    /**
     * Date is used to tell when the payment is created
     */
    @Persisting(2)
    @MetaField(META_DATE)
    var date: Date? = null

    @Persisting(3)
    @MetaField(META_AMOUNT)
    var amount: String? = null

    @Persisting(4)
    @MetaField(META_PAYMENT_ID)
    var paymentId: String? = null

    @Persisting(5)
    @MetaField(META_CONFIRMED)
    var confirmed = false

    /**
     * Confirm Date is used to tell when the worker has confirmed this payment is done
     */
    @Persisting(6)
    @MetaField(META_CONFIRMED_DATE)
    var confirmedDate: Date? = null

    companion object {
        const val STORAGE_KEY = ConnectJobPaymentRecord.STORAGE_KEY

        fun fromV3(oldRecord: ConnectJobPaymentRecordV3): ConnectJobPaymentRecordV21 {
            val newRecord = ConnectJobPaymentRecordV21()
            newRecord.jobId = oldRecord.getJobId()
            newRecord.date = oldRecord.getDate()
            newRecord.amount = oldRecord.getAmount()
            newRecord.paymentId = "-1"
            newRecord.confirmed = false
            newRecord.confirmedDate = Date()
            return newRecord
        }
    }
}
