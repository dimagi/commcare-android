package org.commcare.login

import android.content.Context
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import java.security.SecureRandom

/**
 * Resolves the Connect-managed password for an (appId, username) pair.
 */
class ConnectCredentialResolver(
    private val context: Context,
) {
    fun resolve(
        appId: String,
        username: String,
        createIfNeeded: Boolean,
    ): ResolvedCredentials {
        val existing = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, appId, username)
        val record =
            existing ?: run {
                if (!createIfNeeded) {
                    throw IllegalStateException(
                        "No ConnectLinkedAppRecord found for appId: $appId and username: $username",
                    )
                }
                storeNewRecord(appId, username)
            }
        if (record.isUsingLocalPassphrase) {
            FirebaseAnalyticsUtil.reportCccAppAutoLoginWithLocalPassphrase(appId)
        }
        return ResolvedCredentials(record.password, record)
    }

    private fun storeNewRecord(
        appId: String,
        username: String,
    ): ConnectLinkedAppRecord =
        ConnectAppDatabaseUtil.storeApp(
            context,
            appId,
            username,
            true,
            generateAppPassword(),
            true,
            false,
        )

    private fun generateAppPassword(): String {
        val passwordLength = 20
        val charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_!.?"
        val random = SecureRandom()
        return (1 until passwordLength)
            .map { charSet[random.nextInt(charSet.length)] }
            .joinToString("")
    }
}

data class ResolvedCredentials(
    val password: String,
    val record: ConnectLinkedAppRecord,
)
