package org.commcare.personalId

import org.commcare.CommCareApplication
import org.commcare.android.database.app.models.UserKeyRecord
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.javarosa.core.services.Logger
import java.util.Locale

/**
 * Answers how an app relates to PersonalID: whether Connect manages it, whether PersonalID holds
 * its credentials, and whether the active session really belongs to it.
 */
object PersonalIdAppStateEvaluator {
    @JvmStatic
    fun evaluateAppState(
        appId: String,
        userId: String,
    ): PersonalIdSessionManager.ConnectAppManagement {
        if (ConnectJobUtils.getAppRecord(appId) != null) {
            return PersonalIdSessionManager.ConnectAppManagement.Connect
        }
        return if (PersonalIdCredentialProvider.getCredentialsForApp(appId, userId) != null) {
            PersonalIdSessionManager.ConnectAppManagement.PersonalId
        } else {
            PersonalIdSessionManager.ConnectAppManagement.Unmanaged
        }
    }

    /**
     * Whether the seated app is linked to this PersonalID user as a worker — the `workerLinked`
     * flag, set by the HQ worker-link response. Not the same as PersonalID managing the login,
     * which is [PersonalIdCredentialProvider.isPersonalIdLinkedApp].
     */
    @JvmStatic
    fun isSeatedAppWorkerLinked(username: String): Boolean {
        try {
            if (PersonalIdSessionManager.getInstance().isloggedIn()) {
                val seatedAppId = CommCareApplication.instance().currentApp.uniqueId
                val appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(seatedAppId, username)
                return appRecord != null && appRecord.workerLinked
            }
        } catch (e: Exception) {
            Logger.exception("Error while checking PersonalId status after failed token auth", e)
        }
        return false
    }

    /**
     * Whether the active session is genuinely the current user logged into an app — i.e. the app is
     * seated and the session's key record belongs to that user, not a stale session left over from a
     * previously launched app or a different user.
     */
    @JvmStatic
    fun isCurrentPersonalIdUserLoggedIntoApp(appId: String): Boolean {
        if (!CommCareApplication.isSessionActive()) return false

        val instance = CommCareApplication.instance()
        val currentApp = instance.currentApp
        if (currentApp == null || appId != currentApp.uniqueId) return false

        val sessionUuid = instance.session.userKeyRecordUUID ?: return false
        val user = ConnectUserDatabaseUtil.getUser() ?: return false
        val userId = user.userId ?: return false

        val currentUsername = userId.trim().lowercase(Locale.ROOT)
        return instance
            .getAppStorage(UserKeyRecord::class.java)
            .getRecordsForValue(UserKeyRecord.META_SANDBOX_ID, sessionUuid)
            .any { currentUsername == it.username }
    }
}
