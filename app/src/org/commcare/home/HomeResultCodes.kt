package org.commcare.home

/** Result codes that activities started from home hand back to it. */
object HomeResultCodes {
    /** The session was invalidated in the calling activity, so home should resync it and restart navigation. */
    const val RESULT_RESTART = 3

    /** User data was wiped from Advanced Actions, so home should return to login. */
    const val RESULT_DATA_RESET = 4

    /** Forms were dumped or sent over Wi-Fi Direct from Advanced Actions; the result extras carry the count and message. */
    const val RESULT_FORMS_PROCESSED = 5
}
