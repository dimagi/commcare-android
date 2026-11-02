package org.commcare.home

/** Result codes that activities started from home hand back to it. */
object HomeResultCodes {
    /** The session was invalidated in the calling activity, so home should resync it and restart navigation. */
    const val RESULT_RESTART = 3
}
