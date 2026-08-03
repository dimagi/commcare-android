package org.commcare.activities

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization pin for the sync-icon instance-state that must survive an activity `recreate()`.
 *
 * CCCT-2683 relocates `last-icon-trigger` off `SyncCapableCommCareActivity` onto a
 * `SavedStateProvider`, claiming the move is behavior-preserving. The flag is private with no
 * accessor, so the round-trip is asserted via reflection against the field name
 * ([readField]/[writeField]); that scaffolding retires once the field moves.
 *
 * The three launch/nav keys this file also pinned now live on `HomeActivityCoordinator` (CCCT-2679)
 * and are asserted directly in [HomeInstanceStateRecreationTest].
 */
class HomeInstanceStateTest : HomeScreenActivityTest() {
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
