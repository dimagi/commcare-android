package org.commcare.connect.network.connect.models

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectTaskUtils

data class DeliveryAppProgressResponseModel(
    var updatedJob: Boolean = false,
    var hasDeliveries: Boolean = false,
    var hasPayment: Boolean = false,
    var tasks: List<ConnectTaskRecord> = emptyList(),
)

fun DeliveryAppProgressResponseModel.applyToJob(
    job: ConnectJobRecord,
    context: Context,
) {
    if (updatedJob) {
        ConnectJobUtils.upsertJob(withDeliveryFieldsOnStoredJob(job))
    }
    if (hasDeliveries) {
        ConnectJobUtils.storeDeliveries(context, job.deliveries, job.jobUUID, true)
    }
    if (hasPayment) {
        ConnectJobUtils.storePayments(context, job.payments, job.jobUUID, true)
    }
    ConnectTaskUtils.storeTasks(context, tasks, job.jobUUID)
}

/**
 * The job this request started with may be older than storage, since an opportunities refresh can land while
 * it runs, so only the fields the delivery response carries are written over the stored job.
 */
private fun withDeliveryFieldsOnStoredJob(job: ConnectJobRecord): ConnectJobRecord {
    val stored = ConnectJobUtils.getCompositeJob(job.jobUUID) ?: return job
    stored.maxVisits = job.maxVisits
    stored.projectEndDate = job.projectEndDate
    stored.paymentAccrued = job.paymentAccrued
    stored.isUserSuspended = job.isUserSuspended
    stored.lastDeliveryUpdate = job.lastDeliveryUpdate
    return stored
}
