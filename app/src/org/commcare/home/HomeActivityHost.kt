package org.commcare.home

import android.content.Context
import android.content.Intent
import androidx.savedstate.SavedStateRegistryOwner
import org.commcare.views.dialogs.CommCareAlertDialog

/**
 * The capabilities [HomeActivityCoordinator] and its delegates need from a home activity.
 */
interface HomeActivityHost : SavedStateRegistryOwner {
    /** The host as a [Context], for delegates that build intents and read resources. */
    val hostContext: Context

    /** Launch [intent] for [requestCode]; results come back through the host's `onActivityResult`. */
    fun startActivityForResult(
        intent: Intent,
        requestCode: Int,
    )

    /** Show [dialog] on the host, so dialogs survive the host's own dismissal bookkeeping. */
    fun showAlertDialog(dialog: CommCareAlertDialog)

    /**
     * Re-render whatever surface the host uses to present coordinator actions. `StandardHomeActivity`
     * invalidates its options menu; other activities may refresh the options in other ways.
     *
     * Deliberately not named `rebuildOptionsMenu()`: that name is already taken by
     * `CommCareActivity`, and overriding it here would divert every existing caller — including
     * `SyncCapableCommCareActivity.triggerSyncIconRefresh` — away from the sync-icon teardown.
     */
    fun refreshActionSurface()

    /**
     * Optional UI-refresh hook, used by actions like `changeLanguage()`. Hosts without a
     * `WithUIController` implement it as a no-op.
     */
    fun refreshHostUi()

    /**
     * Demo-mode query, consumed only by the launch pipeline's demo halt.
     * Kept separate from [areAppActionsAvailable] because the two coincide for the session-gated hosts
     * but are independent for `OpportunityHomeActivity`, where this is always `false`.
     */
    fun isDemoUser(): Boolean

    /**
     * Per-action availability predicate for the action facade.
     */
    fun areAppActionsAvailable(): Boolean
}
