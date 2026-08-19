package org.commcare.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.test.core.app.ApplicationProvider
import org.commcare.views.dialogs.CommCareAlertDialog

/**
 * Activity-free [HomeActivityHost] for coordinator unit tests.
 *
 * [performRestore], [dispatchOnCreate] and [performSave] mirror the order `ComponentActivity` uses,
 * which is the whole point of this fake: `performRestore` happens inside `Activity.onCreate` while
 * `ON_CREATE` is dispatched only after it returns. Tests rely on being able to drive those two
 * moments independently.
 */
class FakeHomeActivityHost : HomeActivityHost {
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    var demoUser: Boolean = false
    var actionsAvailable: Boolean = true
    var rebuildOptionsMenuCount: Int = 0
    var refreshHostUiCount: Int = 0
    val startedForResult = mutableListOf<Pair<Intent, Int>>()
    val shownDialogs = mutableListOf<CommCareAlertDialog>()

    /** How many observers are registered on this host's lifecycle. */
    val observerCount: Int get() = lifecycleRegistry.observerCount

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val hostContext: Context get() = ApplicationProvider.getApplicationContext()

    override fun startActivityForResult(
        intent: Intent,
        requestCode: Int,
    ) {
        startedForResult += intent to requestCode
    }

    override fun showAlertDialog(dialog: CommCareAlertDialog) {
        shownDialogs += dialog
    }

    override fun rebuildOptionsMenu() {
        rebuildOptionsMenuCount++
    }

    override fun refreshHostUi() {
        refreshHostUiCount++
    }

    override fun isDemoUser(): Boolean = demoUser

    override fun areAppActionsAvailable(): Boolean = actionsAvailable

    /** `ComponentActivity.onCreate` step 1: restore the registry. Must precede [dispatchOnCreate]. */
    fun performRestore(savedState: Bundle? = null) {
        savedStateController.performRestore(savedState)
    }

    /** `ComponentActivity.onCreate` step 2, dispatched only after `Activity.onCreate` returns. */
    fun dispatchOnCreate() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    /** `ComponentActivity.onSaveInstanceState`: collect every registered provider's state. */
    fun performSave(): Bundle = Bundle().also { savedStateController.performSave(it) }
}
