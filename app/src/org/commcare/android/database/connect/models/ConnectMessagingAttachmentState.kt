package org.commcare.android.database.connect.models

enum class ConnectMessagingAttachmentState(
    val value: String,
) {
    WAITING("waiting"),
    QUEUED("queued"),
    REQUESTED("requested"),
    DOWNLOADING("downloading"),
    AVAILABLE("available"),
    FAILED("failed"),
    EXPIRED("expired"),
    ;

    companion object {
        @JvmStatic
        fun fromValue(value: String): ConnectMessagingAttachmentState =
            values().firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown attachment state: '$value'")
    }
}
