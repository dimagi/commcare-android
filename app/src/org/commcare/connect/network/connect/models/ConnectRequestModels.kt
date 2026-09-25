package org.commcare.connect.network.connect.models

import com.google.gson.annotations.SerializedName

data class ConfirmPaymentsRequest(
    @SerializedName("payments") val payments: List<PaymentConfirmationBody>,
)

data class PaymentConfirmationBody(
    @SerializedName("id") val id: String,
    @SerializedName("confirmed") val confirmed: String,
)
