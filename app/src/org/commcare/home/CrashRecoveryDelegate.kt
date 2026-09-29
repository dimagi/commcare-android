package org.commcare.home

import org.commcare.utils.CrashUtil

/**
 * Registers the seated app's identifying data with crash reporting.
 *
 * Session-independent by nature: the data describes the app, not the user session, so this delegate
 * has no `attachSession`/`detachSession`.
 *
 * Driven by an explicit [register] call rather than `ON_CREATE`, because `ON_CREATE` is dispatched
 * only after `Activity.onCreate` returns, too late to tag crashes from the host's startup work.
 *
 * [registerAppData] is injected so tests can observe the call without standing up Crashlytics.
 */
class CrashRecoveryDelegate(
    private val registerAppData: () -> Unit = { CrashUtil.registerAppData() },
) {
    fun register() {
        registerAppData()
    }
}
