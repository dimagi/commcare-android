package org.commcare.connect

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.repository.ConnectSyncPreferences
import org.commcare.connect.workers.ConnectHeartbeatWorker
import org.commcare.connect.workers.ConnectReleaseTogglesWorker
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdBackupCodeReminderHelper
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.personalId.PersonalIdUserPreferences
import org.commcare.pn.workermanager.NotificationsSyncWorkerManager
import org.commcare.preferences.NotificationPrefs
import org.commcare.utils.CrashUtil
import org.commcare.utils.GlobalErrorUtil
import org.commcare.utils.GlobalErrors
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Owns the PersonalID session: what state it is in, and the transitions between those states.
 *
 * Anything that merely needs PersonalID data rather than session state lives elsewhere —
 * see PersonalIdCredentialProvider, PersonalIdAppStateEvaluator and AppLinkPromptHelper.
 *
 * @author dviggiano
 */
class PersonalIdSessionManager private constructor() {
    init {
        // Protect against reflection
        check(manager == null) { "Already initialized." }
    }

    /**
     * Enum representing the current state of PersonalID
     */
    enum class PersonalIdStatus {
        NotIntroduced, // PersonalID is not intoduced to the user
        Registering, // User is in the recovery or registration phase of PersonalID
        LoggedIn, // User Loggedin PersonalID
    }

    enum class ConnectAppManagement {
        Unmanaged,
        PersonalId,
        Connect,
    }

    @Volatile
    var status: PersonalIdStatus = PersonalIdStatus.NotIntroduced

    /**
     * Resolves the session status and, unlike [isloggedIn], reports a corrupt database.
     * Callers that can surface the resulting error to the user should call this on startup; the
     * status itself resolves on demand.
     */
    fun init() {
        resolveStatus()
        if (status == PersonalIdStatus.NotIntroduced && ConnectDatabaseHelper.isDbBroken()) {
            // Corrupt DB, inform user to recover
            GlobalErrorUtil.triggerGlobalError(GlobalErrors.PERSONALID_DB_STARTUP_ERROR)
        }
    }

    private fun resolveStatus() {
        if (status != PersonalIdStatus.NotIntroduced) return
        val user = ConnectUserDatabaseUtil.getUser() ?: return
        val registering =
            user.registrationPhase != ConnectConstants.PERSONAL_ID_USER_STATUS_REGISTERED
        status = if (registering) PersonalIdStatus.Registering else PersonalIdStatus.LoggedIn
        CrashUtil.registerUserData()
    }

    fun isloggedIn(): Boolean {
        resolveStatus()
        return status == PersonalIdStatus.LoggedIn
    }

    private fun scheduleHeartbeat() {
        if (!isloggedIn()) return

        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        val heartbeatRequest =
            PeriodicWorkRequest
                .Builder(
                    ConnectHeartbeatWorker::class.java,
                    PERIODICITY_FOR_HEARTBEAT_IN_HOURS,
                    TimeUnit.HOURS,
                ).addTag(CONNECT_HEARTBEAT_WORKER)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_DELAY_FOR_HEARTBEAT_RETRY,
                    TimeUnit.MILLISECONDS,
                ).build()

        // UPDATE rather than REPLACE: REPLACE cancels and re-enqueues, which makes the worker
        // eligible to run immediately on every unlock. An in-flight run started that way can
        // land after a subsequent sign-out and write to a deleted Connect DB.
        WorkManager.getInstance(CommCareApplication.instance()).enqueueUniquePeriodicWork(
            CONNECT_HEARTBEAT_REQUEST_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            heartbeatRequest,
        )
    }

    fun userUnlockedPersonalId() {
        scheduleHeartbeat()
        NotificationsSyncWorkerManager.schedulePeriodicPushNotificationRetrieval(
            CommCareApplication.instance(),
        )
        CrashUtil.registerUserData()
        ConnectReleaseTogglesWorker.schedulePeriodicFetch(CommCareApplication.instance())
    }

    fun forgetUser(reason: String) {
        if (ConnectDatabaseHelper.dbExists()) {
            FirebaseAnalyticsUtil.reportPersonalIdAccountForgotten(reason)
        }

        status = PersonalIdStatus.NotIntroduced

        // Cancel periodic push notification retrieval when user logs out
        NotificationsSyncWorkerManager.cancelPeriodicPushNotificationRetrieval(
            CommCareApplication.instance(),
        )

        ConnectUserDatabaseUtil.forgetUser()

        // remove notification read / unread preferences
        NotificationPrefs.removeNotificationReadPref(CommCareApplication.instance())

        ConnectReleaseTogglesWorker.cancelPeriodicFetch(CommCareApplication.instance())
        PersonalIdUnlocker.resetSession()
        PersonalIdUserPreferences.clear()
        CommCareApplication.instance().closeUserSession()
    }

    fun onAccountConfigurationSuccess(sessionData: PersonalIdSessionData) {
        createAndSaveConnectUser(sessionData)
        PersonalIdBackupCodeReminderHelper.initialize()
        status = PersonalIdStatus.LoggedIn
        sessionData.featureReleaseToggles?.let { ConnectAppDatabaseUtil.storeReleaseToggles(it) }
        ConnectSyncPreferences.getInstance().markSessionStart()
        userUnlockedPersonalId()
    }

    private fun createAndSaveConnectUser(sessionData: PersonalIdSessionData) {
        ConnectDatabaseHelper.handleReceivedDbPassphrase(sessionData.dbKey)
        val user =
            ConnectUserRecord(
                sessionData.phoneNumber,
                sessionData.personalId,
                sessionData.oauthPassword,
                sessionData.userName,
                sessionData.backupCode.toString(),
                Date(),
                sessionData.photoBase64,
                sessionData.demoUser,
                sessionData.requiredLock,
                sessionData.invitedUser,
            )
        user.email = sessionData.email
        user.registrationPhase = ConnectConstants.PERSONAL_ID_USER_STATUS_REGISTERED
        ConnectUserDatabaseUtil.storeUser(user)
    }

    companion object {
        private const val CONNECT_HEARTBEAT_WORKER = "connect_heartbeat_worker"
        private const val PERIODICITY_FOR_HEARTBEAT_IN_HOURS = 4L
        private const val BACKOFF_DELAY_FOR_HEARTBEAT_RETRY = 5 * 60 * 1000L // 5 mins
        private const val CONNECT_HEARTBEAT_REQUEST_NAME = "connect_hearbeat_periodic_request"

        @Volatile
        private var manager: PersonalIdSessionManager? = null

        @JvmStatic
        fun getInstance(): PersonalIdSessionManager =
            manager ?: synchronized(PersonalIdSessionManager::class.java) {
                manager ?: PersonalIdSessionManager().also { manager = it }
            }

        @JvmStatic
        fun clearInstance() {
            manager = null
        }
    }
}
