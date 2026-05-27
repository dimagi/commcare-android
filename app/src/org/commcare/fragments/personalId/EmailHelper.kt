package org.commcare.fragments.personalId

import android.app.Activity
import androidx.fragment.app.Fragment
import org.commcare.activities.CommCareActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.personalId.PersonalIdRecoveryCompleter
import org.commcare.utils.StringUtils
import org.commcare.views.dialogs.StandardAlertDialog

/**
 * Shared helpers for the PersonalID email-entry and email-OTP-verification.
 *
 */
object EmailHelper {
    // ---------- Auth-arg selection -------------------------------------------------------

    /**
     * Picks the right auth pair for an email OTP API call based on [workflow]:
     *  - [EmailWorkFlow.EXISTING_USER]: the user is already signed up so authenticate with the persisted [ConnectUserRecord]'s
     *    basic-auth credentials.
     *  - [EmailWorkFlow.REGISTRATION] / [EmailWorkFlow.RECOVERY]: the user has a fresh session
     *    token from /users/start_configuration API call.
     */
    private fun buildAuthArgs(
        activity: Activity,
        workflow: EmailWorkFlow,
        sessionData: PersonalIdSessionData?,
    ): Pair<String?, ConnectUserRecord?> =
        when (workflow) {
            EmailWorkFlow.EXISTING_USER -> null to ConnectUserDatabaseUtil.getUser(activity)
            EmailWorkFlow.REGISTRATION, EmailWorkFlow.RECOVERY -> sessionData?.token to null
        }

    // ---------- API calls ----------------------------------------------------------------

    /**
     * Sends an email OTP request
     */
    fun sendEmailOtp(
        activity: Activity,
        email: String,
        workflow: EmailWorkFlow,
        sessionData: PersonalIdSessionData?,
        onSuccess: () -> Unit,
        onFailure: (PersonalIdOrConnectApiErrorCodes, Throwable?) -> Unit,
    ) {
        val (token, user) = buildAuthArgs(activity, workflow, sessionData)
        object : PersonalIdApiHandler<Boolean>() {
            override fun onSuccess(status: Boolean) {
                onSuccess()
            }

            override fun onFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                onFailure(failureCode, t)
            }
        }.sendEmailOtp(activity, email, token, user)
    }

    /**
     * Verifies an email OTP.
     */
    fun verifyEmailOtp(
        activity: Activity,
        email: String,
        otp: String,
        workflow: EmailWorkFlow,
        sessionData: PersonalIdSessionData?,
        onSuccess: () -> Unit,
        onFailure: (PersonalIdOrConnectApiErrorCodes, Throwable?) -> Unit,
    ) {
        val (token, user) = buildAuthArgs(activity, workflow, sessionData)
        object : PersonalIdApiHandler<Boolean>() {
            override fun onSuccess(status: Boolean) {
                onSuccess()
            }

            override fun onFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                onFailure(failureCode, t)
            }
        }.verifyEmailOtp(activity, email, otp, token, user)
    }

    // ---------- Workflow routing ---------------------------------------------------------

    /**
     * Dispatches the next step after the user skips / declines the email step.
     *  - [EmailWorkFlow.EXISTING_USER]: finish the host activity.
     *  - [EmailWorkFlow.RECOVERY]: finalize account recovery, then call [onRecoverySuccess]
     *    to navigate to the success message screen.
     *  - [EmailWorkFlow.REGISTRATION]: call [onRegistration] to continue to the next signup
     *    step (Photo Capture).
     *
     * The two navigation lambdas live with the caller because each fragment owns its own
     * Safe Args Directions class.
     */
    fun routeAfterEmailDeclined(
        fragment: Fragment,
        workflow: EmailWorkFlow,
        sessionData: PersonalIdSessionData?,
        onRegistration: () -> Unit,
        onRecoverySuccess: () -> Unit,
    ) {
        when (workflow) {
            EmailWorkFlow.EXISTING_USER -> {
                fragment.requireActivity().finish()
            }

            EmailWorkFlow.RECOVERY -> {
                finalizeRecoveryAndShowSuccess(
                    fragment.requireActivity(),
                    sessionData!!,
                    onRecoverySuccess,
                )
            }

            EmailWorkFlow.REGISTRATION -> {
                onRegistration()
            }
        }
    }

    /**
     * Finalizes account recovery (writes ConnectUserRecord, fires analytics, fires the
     * second-device notification) and then invokes [onSuccessNavigate] — typically to
     * navigate to the recovery-success message screen.
     */
    fun finalizeRecoveryAndShowSuccess(
        activity: Activity,
        sessionData: PersonalIdSessionData,
        onSuccessNavigate: () -> Unit,
    ) {
        PersonalIdRecoveryCompleter.finalizeAccountRecovery(activity, sessionData)
        onSuccessNavigate()
    }

    fun isValidEmail(email: String?) = StringUtils.isValidEmail(email)
}
