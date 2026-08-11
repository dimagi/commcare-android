package org.commcare.activities

import android.view.Menu
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.commcare.android.util.ActivityAssertions.assertStarted
import org.commcare.android.util.ActivityAssertions.assertStartedForResult
import org.commcare.appupdate.AppUpdateControllerFactory
import org.commcare.appupdate.AppUpdateState
import org.commcare.appupdate.FlexibleAppUpdateController
import org.commcare.dalvik.R
import org.commcare.preferences.HiddenPreferences
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.fakes.RoboMenu

/**
 * Characterization pins for the home options menu: which items a normal and a demo user see, and
 * where selecting each of them goes.
 *
 * Every item `onOptionsItemSelected` handles has a routing row. Four leave an intent; the other three
 * open a dialog, asserted as the alert-dialog fragment the activity shows.
 *
 * Driven through [StandardHomeActivity] only; [RootMenuHomeActivity] overrides
 * `onOptionsItemSelected` and needs its own rows.
 */
class HomeMenuTest : BaseHomeScreenActivityTest() {
    private val updateController = mockk<FlexibleAppUpdateController>(relaxed = true)

    // region item visibility

    @Test
    fun `normal user sees management menu items`() {
        val (_, menu) = homeWithMenu()

        assertTrue(menu.findItem(R.id.action_update).isVisible)
        assertTrue(menu.findItem(R.id.action_saved_forms).isVisible)
        assertTrue(menu.findItem(R.id.action_preferences).isVisible)
        assertTrue(menu.findItem(R.id.action_advanced).isVisible)
        assertTrue(menu.findItem(R.id.action_about).isVisible)
        assertTrue(menu.findItem(R.id.action_change_language).isVisible)
    }

    @Test
    fun `demo user sees only the language item`() {
        // onPrepareOptionsMenu gates every management item on !isDemoUser(); language is the one
        // item passed setVisible(true) unconditionally, so it stays available to a demo user.
        seatDemoUser()
        val (_, menu) = homeWithMenu()

        assertFalse(menu.findItem(R.id.action_update).isVisible)
        assertFalse(menu.findItem(R.id.action_saved_forms).isVisible)
        assertFalse(menu.findItem(R.id.action_preferences).isVisible)
        assertFalse(menu.findItem(R.id.action_advanced).isVisible)
        assertFalse(menu.findItem(R.id.action_about).isVisible)
        assertTrue("language must stay available in demo mode", menu.findItem(R.id.action_change_language).isVisible)
    }

    // endregion

    // region set-pin item

    @Test
    fun `set pin item hidden when the pin feature is off`() {
        val (_, menu) = homeWithMenu()

        assertFalse(menu.findItem(R.id.action_set_pin).isVisible)
    }

    @Test
    fun `set pin item offers creation when the feature is on and no pin is set`() {
        offerPinForLogin()
        val (_, menu) = homeWithMenu()

        val setPin = menu.findItem(R.id.action_set_pin)
        assertTrue(setPin.isVisible)
        assertEquals(Localization.get("home.menu.pin.set"), setPin.title.toString())
    }

    @Test
    fun `set pin item offers a change when a pin is already set`() {
        offerPinForLogin()
        assignPinToCurrentUser()
        val (_, menu) = homeWithMenu()

        val setPin = menu.findItem(R.id.action_set_pin)
        assertTrue(setPin.isVisible)
        assertEquals(Localization.get("home.menu.pin.change"), setPin.title.toString())
    }

    @Test
    fun `set pin item hidden for a demo user even when the feature is on`() {
        offerPinForLogin()
        seatDemoUser()
        val (_, menu) = homeWithMenu()

        assertFalse(menu.findItem(R.id.action_set_pin).isVisible)
    }

    // endregion

    // region commcare-update item

    @Test
    fun `commcare update item hidden by default`() {
        // showCommCareUpdateMenu starts false and is only flipped by the in-app update check.
        val (_, menu) = homeWithMenu()

        assertFalse(menu.findItem(R.id.action_update_commcare).isVisible)
    }

    @Test
    fun `commcare update item shown once the user has dismissed the update enough times`() {
        val home = buildHomeReportingAvailableUpdate(timesDismissed = MAX_CC_UPDATE_CANCELLATION + 1)

        val menu = menuFor(home)

        assertTrue(menu.findItem(R.id.action_update_commcare).isVisible)
    }

    @Test
    fun `an available update starts straight away rather than offering the menu item`() {
        val home = buildHomeReportingAvailableUpdate(timesDismissed = MAX_CC_UPDATE_CANCELLATION)

        val menu = menuFor(home)

        assertFalse(menu.findItem(R.id.action_update_commcare).isVisible)
        verify(exactly = 1) { updateController.startUpdate(home) }
    }

    @Test
    fun `commcare update item hidden for a demo user even when an update is available`() {
        seatDemoUser()
        val home = buildHomeReportingAvailableUpdate(timesDismissed = MAX_CC_UPDATE_CANCELLATION + 1)

        val menu = menuFor(home)

        assertFalse(menu.findItem(R.id.action_update_commcare).isVisible)
    }

    // endregion

    // region routing: items that launch an activity

    @Test
    fun `selecting update launches the app update activity`() {
        val home = selectItem(R.id.action_update)

        val started = assertStarted(home, UpdateActivity::class.java)
        assertFalse(
            "menu-triggered updates should let the user drive the install",
            started.getBooleanExtra(UpdateActivity.KEY_PROCEED_AUTOMATICALLY, true),
        )
    }

    @Test
    fun `selecting saved forms launches the form record list`() {
        val home = selectItem(R.id.action_saved_forms)

        // goToFormArchive uses startActivityForResult, not startActivity.
        assertStartedForResult(home, FormRecordListActivity::class.java)
    }

    @Test
    fun `selecting settings launches the commcare preference screen`() {
        val home = selectItem(R.id.action_preferences)

        val started = assertStartedForResult(home, SessionAwarePreferenceActivity::class.java)
        assertEquals(
            CommCarePreferenceActivity.PREF_TYPE_COMMCARE,
            started.getStringExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE),
        )
    }

    @Test
    fun `selecting advanced launches the same preference screen in advanced-actions mode`() {
        // Settings and advanced actions differ only by this extra, which is the whole routing
        // contract: swapping the two would otherwise be invisible.
        val home = selectItem(R.id.action_advanced)

        val started = assertStartedForResult(home, SessionAwarePreferenceActivity::class.java)
        assertEquals(
            CommCarePreferenceActivity.PREF_TYPE_ADVANCED_ACTIONS,
            started.getStringExtra(CommCarePreferenceActivity.EXTRA_PREF_TYPE),
        )
    }

    @Test
    fun `selecting set pin launches pin authentication first`() {
        offerPinForLogin()

        val home = selectItem(R.id.action_set_pin)

        // Changing a PIN re-authenticates before CreatePinActivity; that second hop is pinned in
        // HomeActivityResultTest, which is where the authentication result comes back.
        assertStartedForResult(home, PinAuthenticationActivity::class.java)
    }

    // endregion

    // region routing: items that open a dialog

    @Test
    fun `selecting change language opens the locale chooser`() {
        val home = selectItem(R.id.action_change_language)

        assertNotNull("selecting change language should raise a dialog", alertDialog(home))
    }

    @Test
    fun `selecting about opens the about dialog`() {
        val home = selectItem(R.id.action_about)

        assertNotNull("selecting about should raise a dialog", alertDialog(home))
    }

    // endregion

    // region routing: the update item

    @Test
    fun `selecting update commcare hands off to the play store update`() {
        val home = buildHomeReportingAvailableUpdate(timesDismissed = MAX_CC_UPDATE_CANCELLATION + 1)

        home.onOptionsItemSelected(menuFor(home).findItem(R.id.action_update_commcare))

        // startUpdate is also called once by handleAppUpdate() in the under-the-limit arm above;
        // here the counter is over the limit, so the only call can be the user's.
        verify(exactly = 1) { updateController.startUpdate(home) }
    }

    // endregion

    /** Build home laid out, select [itemId] from its options menu, and return the activity. */
    private fun selectItem(itemId: Int): StandardHomeActivity {
        val home = buildVisibleHome()
        home.onOptionsItemSelected(menuFor(home).findItem(itemId))
        return home
    }

    /** The alert dialog fragment home is showing, or null. */
    private fun alertDialog(home: StandardHomeActivity): Any? {
        home.supportFragmentManager.executePendingTransactions()
        return home.currentAlertDialog
    }

    /**
     * Build home with the in-app update check reporting an available update, having already been
     * dismissed [timesDismissed] times, and let the activity handle that state change.
     *
     * The controller is a stand-in for the Play Store's update manager. Home creates it through
     * [AppUpdateControllerFactory] and keeps it private, so the factory is stubbed to hand back the
     * fake and to capture the callback home registers — running that callback is what a real
     * controller does when the update state changes.
     */
    private fun buildHomeReportingAvailableUpdate(timesDismissed: Int): StandardHomeActivity {
        every { updateController.getStatus() } returns AppUpdateState.AVAILABLE
        every { updateController.availableVersionCode() } returns AVAILABLE_VERSION_CODE
        val onUpdateStateChanged = slot<Runnable>()
        mockkStatic(AppUpdateControllerFactory::class)
        every { AppUpdateControllerFactory.create(capture(onUpdateStateChanged), any()) } returns updateController
        repeat(timesDismissed) {
            HiddenPreferences.incrementCommCareUpdateCancellationCounter(AVAILABLE_VERSION_CODE.toString())
        }

        val home = buildHome()
        onUpdateStateChanged.captured.run()
        return home
    }

    /** Build home and return it with its options menu created + prepared. */
    private fun homeWithMenu(): Pair<StandardHomeActivity, Menu> {
        val home = buildHome()
        return home to menuFor(home)
    }

    /** Create + prepare the options menu on an already-built home, so a test can set state first. */
    private fun menuFor(home: StandardHomeActivity): Menu =
        RoboMenu(home).also {
            home.onCreateOptionsMenu(it)
            home.onPrepareOptionsMenu(it)
        }

    companion object {
        /** Matches `HomeScreenBaseActivity.MAX_CC_UPDATE_CANCELLATION`, which is private. */
        private const val MAX_CC_UPDATE_CANCELLATION = 3
        private const val AVAILABLE_VERSION_CODE = 999
    }
}
