package org.commcare.login

data class LaunchContext(
    val startFromLogin: Boolean,
    val manualSwitchToPwMode: Boolean,
)

object PostLoginRouter {
    @JvmStatic
    fun route(
        result: LoginResult,
        launchContext: LaunchContext,
    ): PostLoginDestination =
        when (result) {
            is LoginResult.Success ->
                PostLoginDestination.Home(
                    loginMode = result.loginMode,
                    startFromLogin = launchContext.startFromLogin,
                    manualSwitchToPwMode = launchContext.manualSwitchToPwMode,
                    personalIdManagedLogin = result.personalIdManagedLogin,
                )

            is LoginResult.Failed ->
                PostLoginDestination.TerminalFailure(result.error)
        }
}
