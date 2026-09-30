package org.commcare.home

import org.commcare.services.CommCareSessionService

/**
 * The slice of a live user session that the home coordinator and its delegates read.
 */
interface SeatedAppSession {
    /** Whether the Play Core in-app update prompt is still allowed in this session. */
    fun shouldShowInAppUpdate(): Boolean

    /** Suppress the in-app update prompt for the remainder of this session. */
    fun hideInAppUpdate()
}

/** Adapts the bound [CommCareSessionService] to [SeatedAppSession]. */
class ServiceBackedSession(
    private val service: CommCareSessionService,
) : SeatedAppSession {
    override fun shouldShowInAppUpdate(): Boolean = service.shouldShowInAppUpdate()

    override fun hideInAppUpdate() = service.hideInAppUpdate()
}
