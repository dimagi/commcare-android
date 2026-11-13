package org.commcare.home

/** Every request code home starts an activity with, kept in one place so no two share a value. */
object HomeRequestCodes {
    /** A menu list or menu grid. */
    const val GET_COMMAND = 1

    /** EntitySelectActivity to pick a case, or EntityDetailActivity to confirm an auto-selected one. */
    const val GET_CASE = 2
    const val GET_REMOTE_DATA = 3

    /** FormEntryActivity. */
    const val MODEL_RESULT = 4
    const val MAKE_REMOTE_POST = 5
    const val GET_INCOMPLETE_FORM = 6
    const val PREFERENCES_ACTIVITY = 7
    const val ADVANCED_ACTIONS_ACTIVITY = 8
    const val CREATE_PIN = 9
    const val AUTHENTICATION_FOR_PIN = 10

    /** Whether [requestCode] is one home starts screens with, as opposed to one of the host's own. */
    @JvmStatic
    fun isHomeRequest(requestCode: Int): Boolean = requestCode in GET_COMMAND..AUTHENTICATION_FOR_PIN
}
