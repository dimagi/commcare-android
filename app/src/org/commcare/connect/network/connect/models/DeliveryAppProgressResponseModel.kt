package org.commcare.connect.network.connect.models

import android.content.Context
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.database.ConnectJobUtils

data class DeliveryAppProgressResponseModel(
    var updatedJob: Boolean = false,
    var hasDeliveries: Boolean = false,
    var hasPayment: Boolean = false,
    var parsedTasks: List<ParsedConnectTask> = emptyList(),
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
    job.syncRelearnTasksPrefs(parsedTasks)
}
