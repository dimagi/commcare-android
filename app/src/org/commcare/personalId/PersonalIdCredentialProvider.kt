package org.commcare.personalId

import org.commcare.CommCareApplication
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.personalId.ConnectSsoSyncHelper
import org.commcare.core.network.AuthInfo
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger
import java.util.Date

/**
 * Reads the credentials PersonalID holds for the user and for individual linked apps.
 * Everything here returns null unless PersonalID is signed in.
 */
object PersonalIdCredentialProvider {
    /** The PersonalID token for the signed-in user, or null when absent or expired. */
    @JvmStatic
    fun getConnectToken(): AuthInfo.TokenAuth? {
        if (!PersonalIdSessionManager.getInstance().isloggedIn()) return null
        val user = ConnectUserDatabaseUtil.getUser() ?: return null
        val now = Date()
        if (now < user.connectTokenExpiration) {
            Logger.log(
                LogTypes.TYPE_MAINTENANCE,
                "Found a valid existing Connect Token with current date set to $now" +
                    " and record expiration date being ${user.connectTokenExpiration}",
            )
            return AuthInfo.TokenAuth(user.connectToken)
        }
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Existing Connect token is not valid")
        return null
    }

    /** Whether PersonalID manages the login for this app and user. */
    @JvmStatic
    fun isPersonalIdLinkedApp(
        appId: String,
        username: String,
    ): Boolean {
        if (!PersonalIdSessionManager.getInstance().isloggedIn()) return false
        val record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(appId, username)
        return record != null && record.personalIdLinked
    }

    @JvmStatic
    fun getCredentialsForApp(
        appId: String,
        userId: String,
    ): AuthInfo.ProvidedAuth? {
        val record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(appId, userId)
        if (isPersonalIdLinkedApp(appId, userId) && record.password.isNotEmpty()) {
            return AuthInfo.ProvidedAuth(record.userId, record.password, false)
        }
        return null
    }

    @JvmStatic
    fun getStoredPasswordForApp(
        appId: String,
        userId: String,
    ): String? = getCredentialsForApp(appId, userId)?.password

    /** The stored HQ token for this app, or null when absent or expired. */
    @JvmStatic
    fun getTokenCredentialsForApp(
        appId: String,
        userId: String,
    ): AuthInfo.TokenAuth? {
        if (!PersonalIdSessionManager.getInstance().isloggedIn()) return null
        val record = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(appId, userId) ?: return null
        return if (Date() < record.hqTokenExpiration) AuthInfo.TokenAuth(record.hqToken) else null
    }

    private fun isSeatedAppLinkedToPersonalId(username: String): Boolean {
        if (!PersonalIdSessionManager.getInstance().isloggedIn()) return false
        val seatedAppId = CommCareApplication.instance().currentApp.uniqueId
        return isPersonalIdLinkedApp(seatedAppId, username)
    }

    /** Fetches a fresh HQ SSO token, but only for a seated app that PersonalID manages. */
    @JvmStatic
    fun getHqTokenIfLinked(username: String): AuthInfo.TokenAuth? {
        val user = ConnectUserDatabaseUtil.getUser() ?: return null
        if (!isSeatedAppLinkedToPersonalId(username)) return null
        val seatedAppId = CommCareApplication.instance().currentApp.uniqueId
        val appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(seatedAppId, username)
        return ConnectSsoSyncHelper.retrieveHqSsoTokenSync(
            CommCareApplication.instance(),
            user,
            appRecord,
            username,
            false,
        )
    }
}
