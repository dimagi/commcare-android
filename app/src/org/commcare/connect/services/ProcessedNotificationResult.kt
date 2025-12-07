package org.commcare.connect.services

import org.commcare.android.database.connect.models.PushNotificationRecord

/**
 * Result of processing notification data containing separated notifications for different handling
 */
data class ProcessedNotificationResult(
    val savedNotifications: List<PushNotificationRecord>,
    val messagingNotificationIds: List<String>,
    val savedNotificationIds: List<String>
)
