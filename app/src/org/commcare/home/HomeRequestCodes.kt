package org.commcare.home

/**
 * Every request code home starts an activity with, kept in one place so no two share a value. They sit in a
 * block of their own so they can't clash with a host's own request codes.
 */
object HomeRequestCodes {
    /** A menu list or menu grid. */
    const val GET_COMMAND = 9001

    /** EntitySelectActivity to pick a case, or EntityDetailActivity to confirm an auto-selected one. */
    const val GET_CASE = 9002
    const val GET_REMOTE_DATA = 9003

    /** FormEntryActivity. */
    const val MODEL_RESULT = 9004
    const val MAKE_REMOTE_POST = 9005
    const val GET_INCOMPLETE_FORM = 9006
    const val PREFERENCES_ACTIVITY = 9007
    const val ADVANCED_ACTIONS_ACTIVITY = 9008
    const val CREATE_PIN = 9009
    const val AUTHENTICATION_FOR_PIN = 9010

    /** Whether [requestCode] is one home starts screens with, as opposed to one of the host's own. */
    @JvmStatic
    fun isHomeRequest(requestCode: Int): Boolean = requestCode in GET_COMMAND..AUTHENTICATION_FOR_PIN
}
