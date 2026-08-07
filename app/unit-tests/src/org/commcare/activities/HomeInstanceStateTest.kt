package org.commcare.activities

import org.commcare.android.util.ReflectionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization pins for the launch/nav instance-state that must survive an activity `recreate()`.
 *
 * CCCT-2679 and CCCT-2683 move these four keys onto the coordinator's `SavedStateProvider`, claiming
 * the move preserves behaviour. Nothing exercised `recreate()` before this, so the round-trip had no
 * net.
 *
 * The flags are private, have no accessors, and reach no view or intent, so there is nothing to
 * observe them through: the round-trip is asserted against the field names via [ReflectionUtils].
 * That scaffolding retires once the fields live behind the coordinator.
 */
class HomeInstanceStateTest : HomeScreenActivityTest() {
    @Test
    fun `launch and nav flags survive recreate`() {
        val controller = buildStandardHomeController()
        val home = controller.get()
        ReflectionUtils.writeField(home, "wasExternal", true)
        ReflectionUtils.writeField(home, "loginExtraWasConsumed", true)
        ReflectionUtils.writeField(home, "pendingEndpointNavigationAfterSync", true)

        controller.recreate()

        val recreated = controller.get()
        assertTrue("wasExternal did not survive recreate", ReflectionUtils.readField(recreated, "wasExternal") as Boolean)
        assertTrue(
            "loginExtraWasConsumed did not survive recreate",
            ReflectionUtils.readField(recreated, "loginExtraWasConsumed") as Boolean,
        )
        assertTrue(
            "pendingEndpointNavigationAfterSync did not survive recreate",
            ReflectionUtils.readField(recreated, "pendingEndpointNavigationAfterSync") as Boolean,
        )
    }

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
