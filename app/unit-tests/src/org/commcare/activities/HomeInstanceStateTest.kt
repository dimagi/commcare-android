package org.commcare.activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization pins for the launch/nav instance-state that must survive an activity
 * `recreate()`.
 *
 * CCCT-2679 relocates three keys off `HomeScreenBaseActivity` onto the coordinator's
 * `SavedStateProvider` (`was_external`, `login_extra_was_consumed`,
 * `pending_endpoint_nav_after_sync`); CCCT-2683 does the same for `last-icon-trigger` on
 * `SyncCapableCommCareActivity`. Both slices claim the move is behavior-preserving, and 2679's
 * acceptance is literally "the keys survive `recreate()`". Nothing in the suite exercised
 * `recreate()` before this, so the round-trip had no net.
 *
 * The flags are private with no accessors, so the round-trip is asserted via reflection against
 * the field names ([readField]/[writeField]). That scaffolding retires once the fields live behind
 * the coordinator's `SavedStateProvider`, which the refactor can then assert against directly.
 */
class HomeInstanceStateTest : HomeScreenActivityTest() {
    @Test
    fun `launch and nav flags survive recreate`() {
        val controller = buildStandardHomeController()
        val home = controller.get()
        writeField(home, "wasExternal", true)
        writeField(home, "loginExtraWasConsumed", true)
        writeField(home, "pendingEndpointNavigationAfterSync", true)

        controller.recreate()

        val recreated = controller.get()
        assertTrue("wasExternal did not survive recreate", readField(recreated, "wasExternal") as Boolean)
        assertTrue(
            "loginExtraWasConsumed did not survive recreate",
            readField(recreated, "loginExtraWasConsumed") as Boolean,
        )
        assertTrue(
            "pendingEndpointNavigationAfterSync did not survive recreate",
            readField(recreated, "pendingEndpointNavigationAfterSync") as Boolean,
        )
    }

    @Test
    fun `last icon trigger survives recreate`() {
        val controller = buildStandardHomeController()
        val home = controller.get()

        // Force a value distinct from the fresh-create default so the assertion is meaningful,
        // without importing the private SyncIconTrigger enum. (Default after create is NO_ANIMATION.)
        val current = readField(home, "lastIconTrigger")!!
        val distinct = current.javaClass.enumConstants.first { it != current }
        writeField(home, "lastIconTrigger", distinct)

        controller.recreate()

        assertEquals(distinct, readField(controller.get(), "lastIconTrigger"))
    }
}
