package org.commcare.activities

import org.commcare.android.util.ReflectionUtils
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization pin for the sync-icon instance-state that must survive an activity `recreate()`.
 *
 * CCCT-2683 moves `last-icon-trigger` off `SyncCapableCommCareActivity` onto the coordinator's
 * `SavedStateProvider`, claiming the move preserves behaviour. Nothing exercised `recreate()` before
 * this, so the round-trip had no net.
 *
 * The flag is private, has no accessor, and reaches no view or intent, so there is nothing to
 * observe it through: the round-trip is asserted against the field name via [ReflectionUtils]. That
 * scaffolding retires once the field lives behind the coordinator.
 *
 * The three launch/nav keys this file also pinned now live on `HomeActivityCoordinator` (CCCT-2679)
 * and are asserted directly, without reflection, in [HomeInstanceStateRecreationTest].
 */
class HomeInstanceStateTest : BaseHomeScreenActivityTest() {
    @Test
    fun `last icon trigger survives recreate`() {
        val controller = buildStandardHomeController()
        val home = controller.get()

        // Force a value distinct from the fresh-create default so the assertion is meaningful,
        // without importing the private SyncIconTrigger enum. (Default after create is NO_ANIMATION.)
        val current = ReflectionUtils.readField(home, "lastIconTrigger")!!
        val distinct = current.javaClass.enumConstants.first { it != current }
        ReflectionUtils.writeField(home, "lastIconTrigger", distinct)

        controller.recreate()

        assertEquals(distinct, ReflectionUtils.readField(controller.get(), "lastIconTrigger"))
    }
}
