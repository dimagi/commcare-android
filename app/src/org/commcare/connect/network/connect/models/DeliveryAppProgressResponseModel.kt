package org.commcare.connect.network.connect.models

data class DeliveryAppProgressResponseModel(
    var updatedJob: Boolean = false,
    var hasDeliveries: Boolean = false,
    var hasPayment: Boolean = false,
    var hasTasks: Boolean = false,
    var parsedTasks: List<ParsedConnectTask> = emptyList(),
)
