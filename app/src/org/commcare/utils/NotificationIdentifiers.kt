package org.commcare.utils

import kotlin.math.abs

/**
 * Constants for notification identifiers used across CommCare
 */
object NotificationIdentifiers {
    const val SESSION_SERVICE_NOTIFICATION_ID = 1000
    const val SUBMISSION_NOTIFICATION_ID = 1001
    const val NETWORK_SERVICE_NOTIFICATION_ID = 1002
    const val RECORDING_NOTIFICATION_ID = 1003
    const val MESSAGE_NOTIFICATION_ID = 1004
    const val FCM_NOTIFICATION_ID = 1005

    // Starting point for dynamically generated notification IDs to avoid conflicts with static IDs
    const val DYNAMIC_NOTIFICATION_MIN_ID = 2000

    // Generate a unique notification ID based on the input string. This is useful for notifications when the Id
    // is dynamicaly created
    @JvmStatic
    fun generateNotificationIdFromString(text: String): Int {
        return DYNAMIC_NOTIFICATION_MIN_ID + abs(text.hashCode())
    }
}
