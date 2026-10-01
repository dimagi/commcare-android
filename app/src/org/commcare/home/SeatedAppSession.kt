package org.commcare.home

import org.commcare.CommCareApplication
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
}

/** Adapts the currently bound [CommCareSessionService] to [SeatedAppSession], resolving it per call. */
class ServiceBackedSession : SeatedAppSession {
    override fun shouldShowInAppUpdate(): Boolean = currentService()?.shouldShowInAppUpdate() ?: true

    override fun hideInAppUpdate() {
        currentService()?.hideInAppUpdate()
    }

    private fun currentService(): CommCareSessionService? =
        try {
            CommCareApplication.instance().session
        } catch (e: SessionUnavailableException) {
            null
        }
}
