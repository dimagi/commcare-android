package org.commcare.activities.home

import android.content.Context
import android.content.Intent
import androidx.savedstate.SavedStateRegistryOwner
import org.commcare.views.dialogs.CommCareAlertDialog

/**
 * The capabilities [HomeActivityCoordinator] and its delegates need from a home activity.
 *
 * Extending [SavedStateRegistryOwner] (which extends `LifecycleOwner`) means an `AppCompatActivity`
 * satisfies `getLifecycle()` and `getSavedStateRegistry()` with no code of its own. Keeping the rest
 * of the surface this narrow is what lets `OpportunityHomeActivity` — which is not session-gated and
 * has no `WithUIController` — hold the same coordinator as the two session-gated home activities.
 */
interface HomeActivityHost : SavedStateRegistryOwner {
    /** The host as a [Context], for delegates that build intents and read resources. */
    val hostContext: Context

    /** Launch [intent] for [requestCode]; results come back through the host's `onActivityResult`. */
    fun startActivityForResult(intent: Intent, requestCode: Int)

    /** Show [dialog] on the host, so dialogs survive the host's own dismissal bookkeeping. */
    fun showAlertDialog(dialog: CommCareAlertDialog)

    /**
     * Re-render whatever surface the host uses to present coordinator actions. `StandardHomeActivity`
     * invalidates its options menu; `RootMenuHomeActivity` refreshes its nav drawer. The coordinator
     * deliberately does not know which.
     */
    fun rebuildOptionsMenu()

    /**
     * Optional UI-refresh hook, used by the future `changeLanguage()` action. Hosts without a
     * `WithUIController` implement it as a no-op.
     */
    fun refreshHostUi()

    /**
     * Demo-mode query, consumed only by the future launch pipeline's demo halt.
     * Kept separate from [areActionsAvailable] because the two coincide for the session-gated hosts
     * but are independent for `OpportunityHomeActivity`, where this is always `false`.
     */
    fun isDemoUser(): Boolean

    /**
     * Per-action availability predicate for the future action facade. The session-gated hosts return
     * `!isDemoUser()`; `OpportunityHomeActivity` will return "session attached".
     */
    fun areActionsAvailable(): Boolean
}
