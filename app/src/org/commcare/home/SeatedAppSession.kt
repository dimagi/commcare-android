package org.commcare.home

import org.commcare.CommCareApplication
import org.commcare.preferences.HiddenPreferences
import org.commcare.services.CommCareSessionService
import org.commcare.utils.SessionUnavailableException

/**
 * The slice of a live user session that the home coordinator and its delegates read.
 */
interface SeatedAppSession {
    /** Whether the Play Core in-app update prompt is still allowed in this session. */
    fun shouldShowInAppUpdate(): Boolean

    /** Suppress the in-app update prompt for the remainder of this session. */
    fun hideInAppUpdate()

    /** Whether the user asked never to be offered PIN creation for this app. */
    fun hasDismissedPinCreation(): Boolean

    /** Stop offering PIN creation for this app. */
    fun dismissPinCreation()

    /** Whether the logged-in user has a PIN set. */
    fun hasPinSet(): Boolean

    /** End the user session, sending the user back to login. */
    fun closeUserSession()
}

/** Adapts the currently bound [CommCareSessionService] to [SeatedAppSession], resolving it per call. */
class ServiceBackedSession : SeatedAppSession {
    override fun shouldShowInAppUpdate(): Boolean = currentService()?.shouldShowInAppUpdate() ?: true

    override fun hideInAppUpdate() {
        currentService()?.hideInAppUpdate()
    }

    override fun hasDismissedPinCreation(): Boolean =
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .getBoolean(HiddenPreferences.HAS_DISMISSED_PIN_CREATION, false)

    override fun dismissPinCreation() {
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putBoolean(HiddenPreferences.HAS_DISMISSED_PIN_CREATION, true)
            .apply()
    }

    override fun hasPinSet(): Boolean = CommCareApplication.instance().recordForCurrentUser.hasPinSet()

    override fun closeUserSession() {
        CommCareApplication.instance().closeUserSession()
    }

    private fun currentService(): CommCareSessionService? =
        try {
            CommCareApplication.instance().session
        } catch (e: SessionUnavailableException) {
            null
        }
}

/**
 * A [SeatedAppSession] for a host that can run with no user session: the in-app update prompt
 * waits until a session is live.
 */
class LiveSessionOnlySession(
    private val delegate: SeatedAppSession = ServiceBackedSession(),
    private val isSessionLive: () -> Boolean = ::isUserSessionLive,
) : SeatedAppSession by delegate {
    override fun shouldShowInAppUpdate(): Boolean = isSessionLive() && delegate.shouldShowInAppUpdate()
}

/** Whether a user is logged in to the seated app right now. */
fun isUserSessionLive(): Boolean =
    try {
        CommCareApplication.instance().session
        true
    } catch (e: SessionUnavailableException) {
        false
    }
