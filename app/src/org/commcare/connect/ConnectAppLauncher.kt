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
import java.util.Locale

/**
 * Terminal result of a Connect launch. All non-token, non-seat errors fold into [Retryable];
 * since this path only does PersonalID password logins, there is no SyncFailed or Demo outcome.
 */
sealed class LaunchOutcome {
    object Launched : LaunchOutcome()

    object TokenDenied : LaunchOutcome()

    object AppSeatFailed : LaunchOutcome()

    object CredentialResolutionFailed : LaunchOutcome()

    data class Retryable(
        val error: LoginError,
    ) : LaunchOutcome()
}

/**
 * Seats and signs into a Connect app with the worker's PersonalID credentials without showing
 * [org.commcare.activities.LoginActivity].
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
        fun onOutcome(outcome: LaunchOutcome)
    }

    /** Fire-and-forget [awaitOutcome], scoped to [lifecycleOwner] so it cancels with the view. */
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
    ): LaunchOutcome {
        CommCareApplication.instance().closeUserSession()
        FirebaseAnalyticsUtil.reportCccAppLaunch(
            if (isLearning) "Learn" else "Deliver",
            appId,
        )

        if (seatApp(appId, sink) is SeatResult.Failed) {
            return LaunchOutcome.AppSeatFailed
        }

        val username =
            connectUsername(context)?.trim()?.lowercase(Locale.ROOT)
        if (username.isNullOrEmpty()) {
            return LaunchOutcome.CredentialResolutionFailed
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
            is LoginResult.Success -> LaunchOutcome.Launched
            is LoginResult.Failed ->
                when (result.error) {
                    is LoginError.TokenDenied -> LaunchOutcome.TokenDenied
                    else -> LaunchOutcome.Retryable(result.error)
                }
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
}
