package org.commcare.home

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.commcare.utils.CrashUtil

/**
 * Registers the seated app's identifying data with crash reporting when the host is created.
 *
 * Session-independent by nature: the data describes the app, not the user session, so this delegate
 * has no `attachSession`/`detachSession` and simply rides the host lifecycle.
 *
 * [registerAppData] is injected so tests can observe the call without standing up Crashlytics.
 */
class CrashRecoveryDelegate(
    private val registerAppData: () -> Unit = { CrashUtil.registerAppData() },
) : DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        registerAppData()
    }
}
