package org.commcare.activities

import androidx.appcompat.app.AppCompatActivity
import org.commcare.utils.SessionRegistrationHelper

/**
 * The default [SessionExpirationHandler]: redirects the activity to login when its session is lost.
 */
class LoginRedirectingExpirationHandler(
    private val activity: AppCompatActivity,
) : SessionExpirationHandler {
    override fun startListening() {
        SessionRegistrationHelper.registerSessionExpirationReceiver(activity)
    }

    override fun stopListening() {
        SessionRegistrationHelper.unregisterSessionExpirationReceiver(activity)
    }

    override fun handlePendingExpiration(): Boolean = SessionRegistrationHelper.handleSessionExpiration(activity)

    override fun handleSessionUnavailable() {
        SessionRegistrationHelper.redirectToLogin(activity)
    }
}
