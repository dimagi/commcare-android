package org.commcare.home

import android.os.Bundle
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The flags that describe how the home activity was launched, kept through recreation by the
 * owner's `SavedStateRegistry`.
 */
class HomeLaunchState(
    private val owner: SavedStateRegistryOwner,
) {
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

    /**
     * Consume any saved state and register the provider that writes it back. Idempotent.
     *
     * Also called lazily from the property accessors, because the session-gated hosts read this
     * state inside `onCreateSessionSafe`, which runs during `Activity.onCreate`, before `ON_CREATE`
     * is dispatched.
     */
    fun ensureRestored() {
        if (restored) {
            return
        }
        restored = true
        val registry = owner.savedStateRegistry
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

    private companion object {
        const val PROVIDER_KEY = "org.commcare.home.HomeLaunchState"
        const val KEY_WAS_EXTERNAL = "was_external"
        const val KEY_LOGIN_EXTRA_CONSUMED = "login_extra_was_consumed"
        const val KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC = "pending_endpoint_nav_after_sync"
    }
}
