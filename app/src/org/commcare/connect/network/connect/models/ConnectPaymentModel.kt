package org.commcare.connect.network.connect.models

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord

data class ConnectPaymentConfirmationModel(
    val payment: ConnectJobPaymentRecord,
    val toConfirm: Boolean,
)