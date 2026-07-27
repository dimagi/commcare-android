package org.commcare.activities.home

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Composition root for the behavior shared by every CommCare home screen.
 *
 * Registers itself on the host lifecycle in [init] — before `ON_CREATE` is dispatched — so it
 * observes the host directly instead of being driven by forwarded calls. In this slice it owns only
 * the launch/nav instance state that used to live on `HomeScreenBaseActivity`; the five delegates
 * and the action facade arrive in later slices.
 */
class HomeActivityCoordinator(
    private val host: HomeActivityHost,
) : DefaultLifecycleObserver {
    /** Test-visible proof the observer fired; later slices register delegates from [onCreate]. */
    @VisibleForTesting
    var onCreateCallCount: Int = 0
        private set

    init {
        host.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        onCreateCallCount++
    }
}
