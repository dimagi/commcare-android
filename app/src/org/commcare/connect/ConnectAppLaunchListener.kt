package org.commcare.connect

/** Receives the terminal result of a launch driven by [ConnectAppLaunchController]. */
interface ConnectAppLaunchListener {
    /**
     * Called once the launched app is open in [org.commcare.activities.connect.ConnectActivity].
     *
     * @param menuOpened whether the app's menu opened on top; false when a launch check, such as a
     * sync or an update prompt, took over instead and left the user on this screen
     */
    fun onLaunchSucceeded(menuOpened: Boolean)

    /** Called when the launch terminally fails; the failure has already been logged and reported. */
    fun onLaunchFailed(failure: AppLaunchFailure)
}
