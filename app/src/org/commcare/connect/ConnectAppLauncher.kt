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
import org.commcare.connect.network.LoginInvalidatedException
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.login.AppSeater
import org.commcare.login.AuthSource
import org.commcare.login.LoginController
import org.commcare.login.LoginError
import org.commcare.login.LoginProgressListener
import org.commcare.login.LoginRequest
import org.commcare.login.LoginResult
import org.commcare.login.SeatResult
import org.commcare.utils.GlobalErrorUtil
import org.commcare.utils.GlobalErrors
import org.javarosa.core.services.Logger
import java.util.Locale

/**
 * Terminal result of a Connect launch. Token denials propagate as [LoginInvalidatedException] to the
 * global [org.commcare.utils.CommCareExceptionHandler]; every other non-seat error folds into
 * [Retryable]. This path only does PersonalID password logins, so there is no SyncFailed or Demo outcome.
 */
sealed class LaunchOutcome {
    /**
     * [alreadyLoggedIn] is true when the session was already signed into the target app, so Home can
     * be resumed in place rather than started fresh.
     */
    data class Launched(
        val alreadyLoggedIn: Boolean,
    ) : LaunchOutcome()

    object AppSeatFailed : LaunchOutcome()

    data class Retryable(
        val error: LoginError,
    ) : LaunchOutcome()
}

/**
 * Seats and signs into a Connect app with the worker's PersonalID credentials without showing
 * [org.commcare.activities.LoginActivity].
 */
class ConnectAppLauncher internal constructor(
    private val seatApp: suspend (String, LoginProgressListener) -> SeatResult,
    private val performLogin: suspend (Context, LoginRequest, LoginProgressListener) -> LoginResult,
    private val connectUsername: (Context) -> String?,
    private val isLoggedIntoApp: (String) -> Boolean,
) {
    constructor() : this(
        seatApp = { appId, listener -> AppSeater().seatIfNeeded(appId, listener) },
        performLogin = { context, request, listener -> LoginController(context).performLogin(request, listener) },
        connectUsername = { context -> ConnectUserDatabaseUtil.getUser(context)?.userId },
        isLoggedIntoApp = { appId -> PersonalIdManager.getInstance().isSessionLoggedIntoApp(appId) },
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
        listener: LoginProgressListener,
        callback: OutcomeCallback,
    ): Job =
        lifecycleOwner.lifecycleScope.launch {
            callback.onOutcome(awaitOutcome(context, appId, isLearning, listener))
        }

    suspend fun awaitOutcome(
        context: Context,
        appId: String,
        isLearning: Boolean,
        listener: LoginProgressListener,
    ): LaunchOutcome {
        FirebaseAnalyticsUtil.reportCccAppLaunch(
            if (isLearning) "Learn" else "Deliver",
            appId,
        )

        if (isLoggedIntoApp(appId)) {
            return LaunchOutcome.Launched(alreadyLoggedIn = true)
        }

        CommCareApplication.instance().closeUserSession()

        if (seatApp(appId, listener) is SeatResult.Failed) {
            return LaunchOutcome.AppSeatFailed
        }

        val username =
            connectUsername(context)?.trim()?.lowercase(Locale.ROOT)
        check(!username.isNullOrEmpty()) {
            "Connect launch reached login with no Connect username; a Connect user must exist on this path"
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

        return when (val result = performLogin(context, request, listener)) {
            is LoginResult.Success -> {
                LaunchOutcome.Launched(alreadyLoggedIn = false)
            }

            is LoginResult.Failed -> {
                if (result.error is LoginError.TokenDenied) {
                    GlobalErrorUtil.triggerGlobalError(GlobalErrors.PERSONALID_LOST_CONFIGURATION_ERROR)
                }
                LaunchOutcome.Retryable(result.error)
            }
        }
    }

    private fun hasMultipleMatchingUsers(username: String): Boolean {
        var count = 0
        for (record in CommCareApplication.instance().getAppStorage(UserKeyRecord::class.java)) {
            if (record.username == username && ++count > 1) {
                Logger.exception(
                    "Multiple UserKeyRecords matched a single Connect username during app launch",
                    IllegalStateException("Duplicate Connect username in UserKeyRecord storage"),
                )
                return true
            }
        }
        return false
    }
}
