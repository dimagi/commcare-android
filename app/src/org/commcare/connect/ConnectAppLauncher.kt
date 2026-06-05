package org.commcare.connect

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.commcare.CommCareApplication
import org.commcare.activities.DataPullController.DataPullMode
import org.commcare.activities.LoginMode
import org.commcare.android.database.app.models.UserKeyRecord
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.login.AppSeater
import org.commcare.login.AuthSource
import org.commcare.login.LoginController
import org.commcare.login.LoginError
import org.commcare.login.LoginProgressSink
import org.commcare.login.LoginRequest
import org.commcare.login.LoginResult
import org.commcare.login.SeatResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Terminal result of a silent Connect launch. Every exit from [ConnectAppLauncher.awaitOutcome]
 * produces one of these so the caller can always dismiss its progress UI and act.
 *
 * Sync failures (including BAD_DATA variants) and any non-token, non-seat error fold into
 * [Retryable] rather than distinct variants, and the launcher only issues PersonalID-managed
 * password logins, so there is intentionally no SyncFailed or Demo-mode outcome on this path.
 */
sealed class SilentLaunchOutcome {
    object Launched : SilentLaunchOutcome()

    object AlreadyLaunching : SilentLaunchOutcome()

    object TokenDenied : SilentLaunchOutcome()

    object AppSeatFailed : SilentLaunchOutcome()

    object CredentialResolutionFailed : SilentLaunchOutcome()

    data class Retryable(
        val error: LoginError,
    ) : SilentLaunchOutcome()
}

/**
 * Drives the screen-less launch of a CommCare app from a Connect opportunity: seats the app,
 * authenticates with the worker's PersonalID-managed credentials, and resolves the post-login
 * destination, reporting a [SilentLaunchOutcome] without ever showing
 * [org.commcare.activities.LoginActivity].
 *
 * Construct one per launch via the no-arg constructor; single-flight is enforced process-wide by a
 * shared static guard (see [launching]). Callers may either fire-and-forget with [start] (which
 * binds to a [LifecycleOwner]) or await the outcome directly with [awaitOutcome].
 */
class ConnectAppLauncher internal constructor(
    private val seatApp: suspend (String, LoginProgressSink) -> SeatResult,
    private val performLogin: suspend (Context, LoginRequest, LoginProgressSink) -> LoginResult,
    private val connectUsername: (Context) -> String?,
) {
    constructor() : this(
        seatApp = { appId, sink -> AppSeater().seatIfNeeded(appId, sink) },
        performLogin = { context, request, sink -> LoginController(context).performLogin(request, sink) },
        connectUsername = { context -> ConnectUserDatabaseUtil.getUser(context)?.userId },
    )

    fun interface OutcomeCallback {
        fun onOutcome(outcome: SilentLaunchOutcome)
    }

    /**
     * Fire-and-forget entry point for UI callers: runs [awaitOutcome] in [lifecycleOwner]'s scope so
     * it cancels cleanly when the view is destroyed, then delivers the outcome on the main thread.
     */
    fun start(
        lifecycleOwner: LifecycleOwner,
        context: Context,
        appId: String,
        isLearning: Boolean,
        sink: LoginProgressSink,
        callback: OutcomeCallback,
    ): Job =
        lifecycleOwner.lifecycleScope.launch {
            callback.onOutcome(awaitOutcome(context, appId, isLearning, sink))
        }

    suspend fun awaitOutcome(
        context: Context,
        appId: String,
        isLearning: Boolean,
        sink: LoginProgressSink,
    ): SilentLaunchOutcome {
        if (!launching.compareAndSet(false, true)) {
            return SilentLaunchOutcome.AlreadyLaunching
        }

        try {
            CommCareApplication.instance().closeUserSession()
            FirebaseAnalyticsUtil.reportCccAppLaunch(
                if (isLearning) "Learn" else "Deliver",
                appId,
            )

            if (seatApp(appId, sink) is SeatResult.Failed) {
                return SilentLaunchOutcome.AppSeatFailed
            }

            val username =
                connectUsername(context)?.trim()?.lowercase()
            if (username.isNullOrEmpty()) {
                return SilentLaunchOutcome.CredentialResolutionFailed
            }

            val request =
                LoginRequest(
                    appId = appId,
                    username = username,
                    passwordOrPin = "",
                    credentialType = LoginMode.PASSWORD,
                    authSource = AuthSource.PersonalId,
                    restoreSession = false,
                    triggerMultipleUsersWarning = hasMultipleMatchingUsers(username),
                    blockRemoteKeyManagement = false,
                    dataPullMode = DataPullMode.NORMAL,
                )

            return when (val result = performLogin(context, request, sink)) {
                is LoginResult.Success -> SilentLaunchOutcome.Launched
                is LoginResult.Failed ->
                    when (result.error) {
                        is LoginError.TokenDenied -> SilentLaunchOutcome.TokenDenied
                        else -> SilentLaunchOutcome.Retryable(result.error)
                    }
            }
        } finally {
            launching.set(false)
        }
    }

    private fun hasMultipleMatchingUsers(username: String): Boolean {
        var count = 0
        for (record in CommCareApplication.instance().getAppStorage(UserKeyRecord::class.java)) {
            if (record.username == username && ++count > 1) {
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * Single-flight guard shared across all launcher instances (the caller builds a new one per
         * tap), so one silent launch in progress rejects any other until it finishes — including
         * launches from other Connect surfaces wired up in later phases.
         */
        private val launching = AtomicBoolean(false)
    }
}
