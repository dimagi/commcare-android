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
    private var jobId = 0

    /**
     * Date is used to tell when the payment is created
     */
    @Persisting(2)
    @MetaField(META_DATE)
    private var date: Date? = null

    @Persisting(3)
    @MetaField(META_AMOUNT)
    private var amount: String? = null

    @Persisting(4)
    @MetaField(META_PAYMENT_ID)
    private var paymentId: String? = null

    @Persisting(5)
    @MetaField(META_CONFIRMED)
    private var confirmed = false

    /**
     * Confirm Date is used to tell when the worker has confirmed this payment is done
     */
    @Persisting(6)
    @MetaField(META_CONFIRMED_DATE)
    private var confirmedDate: Date? = null

    companion object {
        const val STORAGE_KEY = ConnectJobPaymentRecord.STORAGE_KEY

        fun fromV21(connectJobPaymentRecordV21: ConnectJobPaymentRecordV21): ConnectJobPaymentRecord {
            val payment = ConnectJobPaymentRecord()
            payment.jobId = connectJobPaymentRecordV21.jobId
            payment.jobUUID = "${connectJobPaymentRecordV21.jobId}"
            payment.paymentUUID = connectJobPaymentRecordV21.paymentId
            payment.date = connectJobPaymentRecordV21.date
            payment.amount = connectJobPaymentRecordV21.amount
            payment.paymentId = connectJobPaymentRecordV21.paymentId
            payment.confirmed = connectJobPaymentRecordV21.confirmed
            payment.confirmedDate = connectJobPaymentRecordV21.confirmedDate
            return payment
        }
    }
}
