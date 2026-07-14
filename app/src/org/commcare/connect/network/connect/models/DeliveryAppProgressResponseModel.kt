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
        ConnectJobUtils.upsertJob(context, job)
    }
    if (hasDeliveries) {
        ConnectJobUtils.storeDeliveries(context, job.deliveries, job.jobUUID, true)
    }
    if (hasPayment) {
        ConnectJobUtils.storePayments(context, job.payments, job.jobUUID, true)
    }
    ConnectTaskUtils.storeTasks(context, tasks, job.jobUUID)
}
