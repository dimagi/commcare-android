package org.commcare.models.connect

data class ConnectDeliveryDetails(
    val unitUUID: String,
    val deliveryName: String,
    val approvedCount: Int,
    val pendingCount: Int,
    val totalAmount: String,
    val remainingDays: Int,
    val approvedPercentage: Double,
)
