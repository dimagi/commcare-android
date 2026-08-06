package org.commcare.activities

import android.view.Menu
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.commcare.appupdate.AppUpdateControllerFactory
import org.commcare.appupdate.AppUpdateState
import org.commcare.appupdate.FlexibleAppUpdateController
import org.commcare.dalvik.R
import org.commcare.preferences.HiddenPreferences
import org.commcare.utils.ConnectivityStatus
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.fakes.RoboMenu
import org.robolectric.shadows.ShadowToast

/**
 * Characterization pins for the traditional home actions a user can invoke: options-menu visibility
 * (demo and non-demo), item-selection routing, and the sync button's offline handling and sub-text.
 * These are the behaviours CCCT-2685 moves behind the coordinator's action facade (`sync()`,
 * `viewSavedForms()`, `updateApp()`, ...) and its paired availability queries.
 *
 * Host coverage: these rows are driven through [StandardHomeActivity] only. `onCreateOptionsMenu`,
 * `onPrepareOptionsMenu` and `onOptionsItemSelected` are all overridden there, and
 * [RootMenuHomeActivity] carries its own `onOptionsItemSelected`, so a green run here is *not* the
 * two-host coverage CCCT-2685's acceptance asks for — the root-menu host needs its own rows once the
 * facade lands.
 *
 * Item-selection routing is pinned for the two destinations that leave an assertable intent
 * (`action_update`, `action_saved_forms`). The remaining six branches — `change_language`,
 * `preferences`, `advanced`, `about`, `set_pin`, `update_commcare` — open dialogs or fragment-hosted
 * preference screens rather than activities, and belong in the 2685 truth-table against facade fakes.
 *
 * Menu access mechanism: [StandardHomeActivity.onCreateOptionsMenu] is called directly with a
 * [RoboMenu], which the real [android.view.MenuInflater] inflates into just fine, then
 * [StandardHomeActivity.onPrepareOptionsMenu] is called on the same instance to apply visibility.
 * This was deterministic; `Shadows.shadowOf(activity).optionsMenu` was not needed.
 *
 * `onOptionsItemSelected` calls the real (no-op under Robolectric) `FirebaseAnalyticsUtil`; it is
 * deliberately NOT static-mocked. Static-mocking `FirebaseAnalyticsUtil` transforms a Google-Play-
 * Services-adjacent class, and the accumulated inline-mock-maker instrumentation corrupts GMS
 * bytecode for unrelated tests later in the same JVM (e.g. PersonalId fragment tests that call the
 * real `GoogleApiAvailability`).
 *
 * The Connect job-progress fetch that `syncButtonPressed()` also triggers is pinned in
 * [HomeConnectJobProgressTest].
 */
class HomeActionsTest : HomeScreenActivityTest() {
    private val updateController = mockk<FlexibleAppUpdateController>(relaxed = true)

    // region options menu visibility

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

    // region set-pin menu item

    @Test
    fun `set pin item hidden when the pin feature is off`() {
        // OFFER_PIN_FOR_LOGIN defaults to off, so this is the default characterization.
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

    // region commcare-update menu item

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

    // region options menu routing

    @Test
    fun `selecting saved forms launches the form record list`() {
        val (home, menu) = homeWithMenu()

        home.onOptionsItemSelected(menu.findItem(R.id.action_saved_forms))

        // goToFormArchive uses startActivityForResult, not startActivity.
        val started = shadowOf(home).nextStartedActivityForResult.intent
        assertEquals(FormRecordListActivity::class.java.name, started.component!!.className)
    }

    @Test
    fun `selecting update launches the update activity`() {
        val (home, menu) = homeWithMenu()

        home.onOptionsItemSelected(menu.findItem(R.id.action_update))

        val started = shadowOf(home).nextStartedActivity
        assertEquals(UpdateActivity::class.java.name, started.component!!.className)
    }

    // endregion

    // region sync button: offline handling

    @Test
    fun `sync in airplane mode toasts the airplane message and reports a notification`() {
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns true
        val home = buildHome()

        home.syncButtonPressed()

        assertEquals(
            Localization.get("notification.sync.airplane.action"),
            ShadowToast.getTextOfLatestToast(),
        )
        assertTrue("a Sync_AirplaneMode notification should be pending", notificationsArePending())
    }

    @Test
    fun `sync with no connection toasts the connections message and reports a notification`() {
        // Offline but NOT in airplane mode: the else branch, which is a different message and a
        // different StockMessage from the airplane row above.
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns false
        val home = buildHome()

        home.syncButtonPressed()

        assertEquals(
            Localization.get("notification.sync.connections.action"),
            ShadowToast.getTextOfLatestToast(),
        )
        assertTrue("a Sync_NoConnections notification should be pending", notificationsArePending())
    }

    @Test
    fun `sync while online clears the airplane-mode notifications`() {
        // Seed a pending notification by going offline first, so the clear is observable. Note the
        // offline block is gated on !isNetworkAvailable alone — airplane mode only picks which
        // message it reports — so isNetworkAvailable must be false here, not just airplane mode true.
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns true
        buildHome().syncButtonPressed()
        assertTrue("fixture failed to leave a notification pending", notificationsArePending())

        every { ConnectivityStatus.isNetworkAvailable(any()) } returns true
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns false
        buildHome().syncButtonPressed()

        assertFalse("going back online should clear the airplane-mode notifications", notificationsArePending())
    }

    // endregion

    // region sync button: sub-text

    @Test
    fun `sub text press starts no activity when no messages pending`() {
        val home = buildHome()

        home.syncSubTextPressed()

        // No pending messages: the notifications view must not be launched.
        assertNull(shadowOf(home).nextStartedActivity)
    }

    @Test
    fun `sub text press launches the notifications view when messages are pending`() {
        reportPendingNotification()
        val home = buildHome()

        home.syncSubTextPressed()

        val started = shadowOf(home).nextStartedActivity
        assertEquals(MessageActivity::class.java.name, started.component!!.className)
    }

    // endregion

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
