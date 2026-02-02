package org.commcare.android.database.connect.models

import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_DATE
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_ENTITY_ID
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_ENTITY_NAME
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_ID
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_JOB_ID
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_REASON
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_STATUS
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_UNIT_NAME
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord.META_SLUG
import org.commcare.android.storage.framework.Persisted
import org.commcare.models.framework.Persisting
import org.commcare.modern.database.Table
import org.commcare.modern.models.MetaField
import java.io.Serializable
import java.util.Date

@Table(ConnectJobDeliveryRecordV21.STORAGE_KEY)
class ConnectJobDeliveryRecordV21 :
    Persisted(),
    Serializable {
    @Persisting(1)
    @MetaField(META_JOB_ID)
    private var jobId = 0

    @Persisting(2)
    @MetaField(META_ID)
    private var deliveryId = 0

    @Persisting(3)
    @MetaField(META_DATE)
    private var date: Date? = null

    @Persisting(4)
    @MetaField(META_STATUS)
    private var status: String? = null

    @Persisting(5)
    @MetaField(META_UNIT_NAME)
    private var unitName: String? = null

    @Persisting(6)
    @MetaField(META_SLUG)
    private var slug: String? = null

    @Persisting(7)
    @MetaField(META_ENTITY_ID)
    private var entityId: String? = null

    @Persisting(8)
    @MetaField(META_ENTITY_NAME)
    private var entityName: String? = null

    @Persisting(9)
    private var lastUpdate: Date? = null

    @Persisting(10)
    @MetaField(META_REASON)
    private var reason: String? = null

    companion object {
        const val STORAGE_KEY = ConnectJobDeliveryRecord.STORAGE_KEY

        fun fromV21(oldRecord: ConnectJobDeliveryRecordV21): ConnectJobDeliveryRecord {
            val newRecord = ConnectJobDeliveryRecord()
            newRecord.jobId = oldRecord.jobId
            newRecord.deliveryId = oldRecord.deliveryId
            newRecord.date = oldRecord.date
            newRecord.status = oldRecord.status
            newRecord.unitName = oldRecord.unitName
            newRecord.slug = oldRecord.slug
            newRecord.entityId = oldRecord.entityId
            newRecord.entityName = oldRecord.entityName
            newRecord.lastUpdate = oldRecord.lastUpdate
            newRecord.reason = oldRecord.reason
            newRecord.deliveryUUID = "${oldRecord.deliveryId}"
            newRecord.jobUUID = "${newRecord.jobId}"
            return newRecord
        }
    }
}
