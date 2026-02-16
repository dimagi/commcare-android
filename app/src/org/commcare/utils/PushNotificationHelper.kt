package org.commcare.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.javarosa.core.services.Logger

object PushNotificationHelper {
    const val MAX_MESSAGE_LENGTH: Int = 65535
    const val MESSAGE_NOTIFICATION_TITLE: String = "notification_title"
    const val NOTIFICATION: String = "notification"
    const val MESSAGE: String = "message"

    @JvmStatic
    fun truncateMessage(message: String, type: String): String {
        if (message.length > MAX_MESSAGE_LENGTH) {
            val errorMessage = "Received " + type + " exceeded max length. Length=" + message.length
            try {
                FirebaseCrashlytics.getInstance()
                    .recordException(Exception(errorMessage))
            } catch (_: Exception) {
            }

            return message.take(MAX_MESSAGE_LENGTH)
        }

        return message
    }
}
