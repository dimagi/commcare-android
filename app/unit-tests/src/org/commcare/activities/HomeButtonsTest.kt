package org.commcare.activities

import io.mockk.every
import org.commcare.android.database.user.models.FormRecord
import org.commcare.android.util.ActivityAssertions.assertStarted
import org.commcare.android.util.ActivityAssertions.assertStartedForResult
import org.commcare.android.util.ActivityAssertions.assertStartedNothing
import org.commcare.utils.ConnectivityStatus
import org.commcare.views.notifications.NotificationMessage
import org.javarosa.core.services.locale.Localization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.shadows.ShadowToast

/**
 * Characterization pins for the home grid's buttons, driven by clicking the rendered cards: where
 * each one goes, and the sync button's offline handling and sub-text.
 *
 * Every button a single-app `form_nav_tests` login renders has a row. The two the profile hides
 * (training, report) and the Connect button are covered where their visibility rules live —
 * [StandardHomePresentationTest] and [HomeConnectJobProgressTest].
 */
class HomeButtonsTest : BaseHomeScreenActivityTest() {
    // region where each button goes

    @Test
    fun `start opens the app's root menu`() {
        val home = buildVisibleHome()

        clickHomeButton(home, Localization.get("home.start"))

        assertStartedForResult(home, MenuActivity::class.java)
    }

    @Test
    fun `saved forms opens the form archive`() {
        val home = buildVisibleHome()

        clickHomeButton(home, Localization.get("home.forms.saved"))

        val started = assertStartedForResult(home, FormRecordListActivity::class.java)
        assertFalse(
            "the saved-forms button must not filter to incomplete forms",
            started.hasExtra(FormRecord.META_STATUS),
        )
    }

    @Test
    fun `incomplete forms opens the archive filtered to incomplete`() {
        // The only thing separating this button from the saved-forms one above is the status extra.
        val home = buildVisibleHome()

        clickHomeButton(home, Localization.get("home.forms.incomplete"))

        val started = assertStartedForResult(home, FormRecordListActivity::class.java)
        assertEquals(FormRecord.STATUS_INCOMPLETE, started.getStringExtra(FormRecord.META_STATUS))
    }

    @Test
    fun `log out closes the session and finishes home`() {
        val home = buildVisibleHome()

        clickHomeButton(home, Localization.get("home.logout"))

        assertTrue("logging out should finish home so dispatch can route to login", home.isFinishing)
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(home).resultCode)
    }

    // endregion

    // region sync button: offline handling

    @Test
    fun `sync in airplane mode toasts and reports the airplane notification`() {
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns true
        val home = buildVisibleHome()

        clickSync(home)

        assertEquals(
            Localization.get("notification.sync.airplane.action"),
            ShadowToast.getTextOfLatestToast(),
        )
        assertReportedNotification("notification.sync.airplane")
    }

    @Test
    fun `sync with no connection toasts and reports the connections notification`() {
        // Offline but not in airplane mode: a different message and StockMessage from the row above.
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns false
        val home = buildVisibleHome()

        clickSync(home)

        assertEquals(
            Localization.get("notification.sync.connections.action"),
            ShadowToast.getTextOfLatestToast(),
        )
        assertReportedNotification("notification.sync.connections")
    }

    @Test
    fun `sync while online clears the airplane-mode notifications`() {
        // The offline block is gated on !isNetworkAvailable alone; airplane mode only picks which
        // message it reports. So seed the notification with the network down, not just airplane on.
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns false
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns true
        clickSync(buildVisibleHome())
        assertTrue("fixture failed to leave a notification pending", notificationsArePending())

        every { ConnectivityStatus.isNetworkAvailable(any()) } returns true
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns false
        clickSync(buildVisibleHome())

        assertFalse("going back online should clear the airplane-mode notifications", notificationsArePending())
    }

    // endregion

    // region sync button: sub-text

    @Test
    fun `sync sub-text goes nowhere when no messages are pending`() {
        val home = buildVisibleHome()

        clickHomeButtonSubText(home, Localization.get("home.sync"))

        assertStartedNothing(home)
    }

    @Test
    fun `sync sub-text opens the notifications view when messages are pending`() {
        reportPendingNotification()
        val home = buildVisibleHome()

        clickHomeButtonSubText(home, Localization.get("home.sync"))

        assertStarted(home, MessageActivity::class.java)
    }

    // endregion

    private fun clickSync(home: StandardHomeActivity) = clickHomeButton(home, Localization.get("home.sync"))

    /**
     * Asserts the only notification home reported is the one built from [localeKeyBase], matching the
     * title and detail the user actually reads rather than just "something is pending".
     */
    private fun assertReportedNotification(localeKeyBase: String) {
        val reported: List<NotificationMessage> = pendingNotifications()
        assertEquals("expected exactly one notification, got ${reported.map { it.title }}", 1, reported.size)
        assertEquals(Localization.get("$localeKeyBase.title"), reported.single().title)
        assertEquals(Localization.get("$localeKeyBase.detail"), reported.single().details)
        assertEquals(AIRPLANE_MODE_CATEGORY, reported.single().category)
    }

    companion object {
        /** Matches `StandardHomeActivity.AIRPLANE_MODE_CATEGORY`, which is private. */
        private const val AIRPLANE_MODE_CATEGORY = "airplane-mode"
    }
}
