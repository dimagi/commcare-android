package org.commcare.activities

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Intent
import org.commcare.CommCareApplication
import org.commcare.android.util.ActivityAssertions.assertStartedForResult
import org.commcare.android.util.ActivityAssertions.assertStartedNothing
import org.commcare.android.util.ActivityAssertions.startedIntents
import org.commcare.session.SessionFrame
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Characterization pins for the session navigation home drives: handing a command out to the menu,
 * taking the chosen command back, and advancing the session from it.
 *
 * The deeper legs of this loop already have coverage that goes through home — `EndOfFormTest`,
 * `FormRecordProcessingTest` and `XFormUpdateInfoTest` all build home, follow it into form entry, and
 * feed the form's result back through `onActivityResult`. What was missing, and is pinned here, is
 * home's own end: the callout it sends, the guard on the result it accepts, and where it goes next.
 *
 * The test app (`form_nav_tests`) has one form and no case datum, so the `GET_CASE` leg has no
 * fixture here; it belongs with a case-list app.
 */
class HomeSessionNavigationTest : BaseHomeScreenActivityTest() {
    @Test
    fun `choosing a command from the menu advances the session into form entry`() {
        val home = buildVisibleHome()
        val callout = calloutToRootMenu(home)

        home.onActivityResultSessionSafe(
            HomeScreenBaseActivity.GET_COMMAND,
            RESULT_OK,
            chose(FORM_COMMAND, callout),
        )

        assertStartedForResult(home, FormEntryActivity::class.java)
    }

    @Test
    fun `backing out of the root menu resets the session and stays home`() {
        val home = buildVisibleHome()
        calloutToRootMenu(home)

        home.onActivityResultSessionSafe(HomeScreenBaseActivity.GET_COMMAND, RESULT_CANCELED, Intent())

        assertStartedNothing(home)
        assertNull("stepping back from the root menu should clear the command", currentCommand())
    }

    @Test
    fun `a command chosen against stale session state resets the session instead of navigating`() {
        // The guard against acting on a callout whose session has moved on underneath it: the
        // returned intent claims the session needed a datum, but it needs a command.
        val home = buildVisibleHome()
        calloutToRootMenu(home)

        val stale = chose(FORM_COMMAND, Intent().putExtra(PENDING_SESSION_DATA, SessionFrame.STATE_DATUM_VAL))
        home.onActivityResultSessionSafe(HomeScreenBaseActivity.GET_COMMAND, RESULT_OK, stale)

        assertStartedNothing(home)
        assertNull("a stale callout must not leave the chosen command seated", currentCommand())
        home.supportFragmentManager.executePendingTransactions()
        assertNotNull("the user should be warned the session was refreshed", home.currentAlertDialog)
    }

    /**
     * Press Start and return the callout home sent to the root menu, with both shadow queues drained
     * so a later assertion sees only what the result triggered.
     */
    private fun calloutToRootMenu(home: StandardHomeActivity): Intent {
        clickHomeButton(home, Localization.get("home.start"))
        return assertStartedForResult(home, MenuActivity::class.java).also { startedIntents(home) }
    }

    /**
     * The result the menu hands back: the chosen command, on top of the pending-session extras home
     * sent out with [callout] and checks on the way back in.
     */
    private fun chose(
        command: String,
        callout: Intent,
    ): Intent = Intent(callout).putExtra(SessionFrame.STATE_COMMAND_ID, command)

    private fun currentCommand(): String? =
        CommCareApplication
            .instance()
            .currentSessionWrapper
            .session
            .command

    companion object {
        private const val FORM_COMMAND = "m0-f0"

        /** Matches `HomeScreenBaseActivity.KEY_PENDING_SESSION_DATA`, which is private. */
        private const val PENDING_SESSION_DATA = "pending-session-data-id"
    }
}
