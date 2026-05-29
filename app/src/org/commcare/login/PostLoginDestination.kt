package org.commcare.login

import org.commcare.activities.LoginMode

sealed class PostLoginDestination {
    data class Home(
        val loginMode: LoginMode,
        val startFromLogin: Boolean,
        val manualSwitchToPwMode: Boolean,
        val personalIdManagedLogin: Boolean,
    ) : PostLoginDestination()

    data class TerminalFailure(
        val error: LoginError,
    ) : PostLoginDestination()
}
