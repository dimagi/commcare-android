package org.commcare.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import org.commcare.activities.SessionExpirationHandler
import org.commcare.utils.SessionRegistrationHelper

/**
 * Notices when the user session goes away underneath the host and reports it through
 * [HomeActivityHost.onSessionLost], leaving the response to the host.
 *
 * - An expiration while the host is resumed arrives as a [SessionRegistrationHelper.USER_SESSION_EXPIRED]
 * broadcast, which this delegate receives on its own receiver.
 * - An expiration while nothing was listening is left as a pending flag in [SessionRegistrationHelper],
 * which [handlePendingExpiration] consumes on resume and on activity result.
 * - A session found missing mid-resume is reported through [handleSessionUnavailable].
 *
 * Called synchronously by `SessionAwareCommCareActivity` rather than observing the lifecycle, because
 * the resume checks have to run before `onResumeSessionSafe`, and lifecycle observers only see
 * `ON_RESUME` after `onResume` returns.
 */
class SessionExpirationDelegate(
    private val host: HomeActivityHost,
    private val consumePendingExpiration: () -> Boolean = SessionRegistrationHelper::consumePendingExpiration,
) : SessionExpirationHandler {
    private var expirationReceiver: BroadcastReceiver? = null

    override fun startListening() {
        if (expirationReceiver != null) {
            return
        }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) {
                    consumePendingExpiration()
                    host.onSessionLost()
                }
            }
        expirationReceiver = receiver
        val filter = IntentFilter(SessionRegistrationHelper.USER_SESSION_EXPIRED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            host.hostContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            host.hostContext.registerReceiver(receiver, filter)
        }
    }

    override fun stopListening() {
        val receiver = expirationReceiver ?: return
        expirationReceiver = null
        host.hostContext.unregisterReceiver(receiver)
    }

    override fun handlePendingExpiration(): Boolean {
        if (!consumePendingExpiration()) {
            return false
        }
        host.onSessionLost()
        return true
    }

    override fun handleSessionUnavailable() {
        host.onSessionLost()
    }
}
