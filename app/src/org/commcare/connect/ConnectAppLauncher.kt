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
 * Terminal result of a silent Connect launch, for the caller to translate into navigation or UI.
 */
sealed class SilentLaunchOutcome {
    data class Launched(
        val success: LoginResult.Success,
    ) : SilentLaunchOutcome()

    object AlreadyLaunching : SilentLaunchOutcome()

    object TokenDenied : SilentLaunchOutcome()

    object AppSeatFailed : SilentLaunchOutcome()

    data class Retryable(
        val error: LoginError,
    ) : SilentLaunchOutcome()
}

/**
 * Drives the screen-less launch of a CommCare app from a Connect opportunity: seats the app and
 * authenticates with the worker's PersonalID-managed credentials behind a process-wide re-entrancy
 * guard, reporting a [SilentLaunchOutcome] without ever showing
 * [org.commcare.activities.LoginActivity].
 */
class ConnectAppLauncher internal constructor(
    private val seatApp: suspend (String, LoginProgressSink) -> SeatResult,
    private val performLogin: suspend (Context, LoginRequest, LoginProgressSink) -> LoginResult,
) {
    constructor() : this(
        seatApp = { appId, sink -> AppSeater().seatIfNeeded(appId, sink) },
        performLogin = { context, request, sink -> LoginController(context).performLogin(request, sink) },
    )

    fun interface OutcomeCallback {
        fun onOutcome(outcome: SilentLaunchOutcome)
    }

    fun start(
        lifecycleOwner: LifecycleOwner,
        context: Context,
        appId: String,
        isLearning: Boolean,
        sink: LoginProgressSink,
        callback: OutcomeCallback,
    ): Job =
        lifecycleOwner.lifecycleScope.launch {
            callback.onOutcome(launch(context, appId, isLearning, sink))
        }

    suspend fun launch(
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
            FirebaseAnalyticsUtil.reportCccAppLaunch(if (isLearning) "Learn" else "Deliver", appId)

            if (seatApp(appId, sink) is SeatResult.Failed) {
                return SilentLaunchOutcome.AppSeatFailed
            }

            val username =
                PersonalIdManager
                    .getInstance()
                    .getConnectUsername(context)
                    .lowercase()
                    .trim()
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
                is LoginResult.Success -> SilentLaunchOutcome.Launched(result)
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
        private val launching = AtomicBoolean(false)
    }
}
