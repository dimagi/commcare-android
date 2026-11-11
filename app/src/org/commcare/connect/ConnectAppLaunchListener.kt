package org.commcare.connect

/** Receives the terminal result of a launch driven by [ConnectAppLaunchController]. */
interface ConnectAppLaunchListener {
    /** Called once Home has been started for the launched app. */
    fun onLaunchSucceeded()

    /** Called when the launch terminally fails; the failure has already been logged and reported. */
    fun onLaunchFailed(failure: AppLaunchFailure)
}
