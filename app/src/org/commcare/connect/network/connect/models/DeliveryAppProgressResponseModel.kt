package org.commcare.connect.network.connect.models

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectTaskUtils
import java.util.Date

/** The job fields are null when the response left them out. */
data class DeliveryAppProgressResponseModel(
    var hasDeliveries: Boolean = false,
    var hasPayment: Boolean = false,
    var tasks: List<ConnectTaskRecord> = emptyList(),
    var maxVisits: Int? = null,
    var projectEndDate: Date? = null,
    var paymentAccrued: Int? = null,
    var isUserSuspended: Boolean? = null,
) {
    val updatedJob: Boolean
        get() = maxVisits != null || projectEndDate != null || paymentAccrued != null || isUserSuspended != null
}

/**
 * The job this request started with may be older than storage, since an opportunities refresh can land while
 * it runs, so only the fields the response sent are written onto the stored job.
 */
fun DeliveryAppProgressResponseModel.applyToJob(
    job: ConnectJobRecord,
    context: Context,
) {
    if (updatedJob) {
        ConnectJobUtils.updateStoredJob(job.jobUUID) { stored ->
            maxVisits?.let { stored.maxVisits = it }
            projectEndDate?.let { stored.projectEndDate = it }
            paymentAccrued?.let { stored.paymentAccrued = it }
            isUserSuspended?.let { stored.isUserSuspended = it }
            stored.lastDeliveryUpdate = Date()
        }
    }
    if (hasDeliveries) {
        ConnectJobUtils.storeDeliveries(context, job.deliveries, job.jobUUID, true)
    }
    if (hasPayment) {
        ConnectJobUtils.storePayments(context, job.payments, job.jobUUID, true)
    }
    ConnectTaskUtils.storeTasks(context, tasks, job.jobUUID)
}
