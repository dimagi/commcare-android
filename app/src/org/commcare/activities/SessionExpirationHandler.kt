package org.commcare.activities

/**
 * How a session-aware activity notices and responds to its user session going away.
 */
interface SessionExpirationHandler {
    /** Start listening for session expiration broadcasts. Called from `onResume`. */
    fun startListening()

    /** Stop listening for session expiration broadcasts. Called from `onPause`. */
    fun stopListening()

    /**
     * Act on a session that expired while nothing was listening.
     *
     * @return true if the session had expired and the loss was handled
     */
    fun handlePendingExpiration(): Boolean

    /** Act on a session found missing while resuming. */
    fun handleSessionUnavailable()
}
