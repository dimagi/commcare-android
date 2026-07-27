package org.commcare.activities.home

import android.content.Intent
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Composition root for the behavior shared by every CommCare home screen.
 *
 * Registers itself on the host lifecycle in [init] — before `ON_CREATE` is dispatched — so it
 * observes the host directly instead of being driven by forwarded calls. It also owns the
 * launch/nav instance state that describes *how the activity was launched*, persisting it through
 * the host's `SavedStateRegistry` rather than a forwarded `onSaveInstanceState`.
 *
 * In this slice it holds no delegates; the five delegates and the action facade arrive in later
 * slices.
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

    /** Test-visible proof the observer fired; later slices register delegates from [onCreate]. */
    @VisibleForTesting
    var onCreateCallCount: Int = 0
        private set

    init {
        host.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        onCreateCallCount++
        // Covers hosts that never touch the launch/nav state, so the provider is registered and
        // the state is still persisted for them.
        ensureRestored()
    }

    /**
     * Fan an activity result out to the delegates that need it. `onActivityResult` is the only
     * cross-cutting host callback with no `Lifecycle` hook, so it stays a forwarded call. No
     * delegates are registered yet, so this currently does nothing but establish the seam.
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
     * `Activity.onCreate` — before `ON_CREATE` is dispatched. `AppCompatActivity.onCreate` has
     * already run `SavedStateRegistryController.performRestore` by then, so consuming here is safe.
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
        private const val PROVIDER_KEY = "org.commcare.activities.home.HomeActivityCoordinator"
        private const val KEY_WAS_EXTERNAL = "was_external"
        private const val KEY_LOGIN_EXTRA_CONSUMED = "login_extra_was_consumed"
        private const val KEY_PENDING_ENDPOINT_NAV_AFTER_SYNC = "pending_endpoint_nav_after_sync"
    }
}
