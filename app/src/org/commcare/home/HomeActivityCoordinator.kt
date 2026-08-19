package org.commcare.home

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Composition root for the behavior shared by every CommCare home screen.
 *
 * - Registers itself on the host lifecycle during init — before `ON_CREATE` is dispatched — so it
 * observes the host directly instead of being driven by forwarded calls.
 * - Owns the launch/nav instance state that describes *how the activity was launched*, persisting
 * it through the host's `SavedStateRegistry`.
 */
class HomeActivityCoordinator(
    private val host: HomeActivityHost,
) : DefaultLifecycleObserver {
    private var restored = false

    private var externalLaunch = false
    private var loginExtraConsumed = false
    private var endpointNavPendingAfterSync = false

    /** Activity was launched by an external app, so form submission may redispatch back to it. */
    var wasExternal: Boolean
        get() {
            ensureRestored()
            return externalLaunch
        }
        set(value) {
            ensureRestored()
            externalLaunch = value
        }

    /** The one-shot `START_FROM_LOGIN` extra has already driven the post-login launch checks. */
    var loginExtraWasConsumed: Boolean
        get() {
            ensureRestored()
            return loginExtraConsumed
        }
        set(value) {
            ensureRestored()
            loginExtraConsumed = value
        }

    /** A session-endpoint launch needs a blocking sync before navigating to the endpoint. */
    var pendingEndpointNavigationAfterSync: Boolean
        get() {
            ensureRestored()
            return endpointNavPendingAfterSync
        }
        set(value) {
            ensureRestored()
            endpointNavPendingAfterSync = value
        }

    init {
        host.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        ensureRestored()
    }

    /**
     * Fan an activity result out to the delegates that need it
     */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        intent: Intent?,
    ) {
        // Intentionally empty: delegates arrive in later slices.
    }

    /**
     * Consume any saved launch/nav state and register the provider that writes it back. Idempotent.
     *
     * Called lazily from the property accessors rather than only from [onCreate] because the
     * session-gated hosts read this state inside `onCreateSessionSafe`, which runs during
     * `Activity.onCreate` — before `ON_CREATE` is dispatched.
     */
    private fun ensureRestored() {
        if (restored) {
            return
        }
        restored = true
        val registry = host.savedStateRegistry
        registry.consumeRestoredStateForKey(PROVIDER_KEY)?.let { state ->
            externalLaunch = state.getBoolean(KEY_WAS_EXTERNAL)
            loginExtraConsumed = state.getBoolean(KEY_LOGIN_EXTRA_CONSUMED)
            endpointNavPendingAfterSync = state.getBoolean(KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC)
        }
        registry.registerSavedStateProvider(PROVIDER_KEY) {
            Bundle().apply {
                putBoolean(KEY_WAS_EXTERNAL, externalLaunch)
                putBoolean(KEY_LOGIN_EXTRA_CONSUMED, loginExtraConsumed)
                putBoolean(KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC, endpointNavPendingAfterSync)
            }
        }
    }

    companion object {
        private const val PROVIDER_KEY = "org.commcare.home.HomeActivityCoordinator"
        private const val KEY_WAS_EXTERNAL = "was_external"
        private const val KEY_LOGIN_EXTRA_CONSUMED = "login_extra_was_consumed"
        private const val KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC = "pending_endpoint_nav_after_sync"
    }
}
