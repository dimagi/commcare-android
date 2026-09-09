package org.commcare.activities

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.database.app.models.UserKeyRecord
import org.commcare.android.mocks.FormAndDataSyncerFake
import org.commcare.android.util.TestAppInstaller
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.dalvik.R
import org.commcare.models.database.user.DemoUserBuilder
import org.commcare.preferences.DeveloperPreferences
import org.commcare.preferences.PrefValues
import org.commcare.utils.ConnectivityStatus
import org.commcare.views.notifications.NotificationMessage
import org.commcare.views.notifications.NotificationMessageFactory
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowLooper

/**
 * Shared base for home-screen characterization tests. Installs a test app + user and exposes helpers
 * to build the home activity.
 *
 * The only thing stubbed here is [ConnectivityStatus], which reports on the device's radios; every
 * other dependency, Connect DB included, runs for real. An empty Connect DB is what makes a plain
 * subclass boot with no job seated; suites that need one seat it with
 * `ConnectTestUtils.seatJob(...)`.
 *
 * Subclasses run under [AndroidJUnit4] with [CommCareTestApplication]; add `@Config(sdk = [...])`
 * on individual tests only when a specific Android level matters.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
abstract class BaseHomeScreenActivityTest {
    @Before
    fun baseSetUp() {
        clearPrefs()
        clearPendingNotifications()
        (CommCareTestApplication.instance() as CommCareTestApplication).initWorkManager()
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)

        // Default to "online, not in airplane mode" so the sync paths don't branch into the
        // offline handling unless a test asks for it.
        mockkStatic(ConnectivityStatus::class)
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns true
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns false
    }

    @After
    fun baseTearDown() {
        unmockkAll()
        // PersonalIdManager is a process-wide singleton holding its own login status, so a test that
        // signs in would otherwise leave the next one logged in.
        PersonalIdManager.getInstance().forgetUser("home test teardown")
        ConnectDatabaseHelper.teardown()
        clearPrefs()
        clearPendingNotifications()
    }

    /** Build the home activity (stopped at `onCreate`) with a fake form syncer injected. */
    protected fun buildHome(
        personalIdManagedLogin: Boolean = false,
        configureIntent: (Intent.() -> Unit)? = null,
    ): StandardHomeActivity = buildStandardHomeController(personalIdManagedLogin, configureIntent).get()

    /**
     * Build home and take it all the way to a laid-out window, so its views are measured, bound and
     * clickable. Needed by anything that reads or clicks the button grid: the grid's children are
     * only created by a layout pass.
     */
    protected fun buildVisibleHome(
        personalIdManagedLogin: Boolean = false,
        configureIntent: (Intent.() -> Unit)? = null,
    ): StandardHomeActivity =
        buildStandardHomeController(personalIdManagedLogin, configureIntent)
            .start()
            .resume()
            .visible()
            .get()
            .also { ShadowLooper.idleMainLooper() }

    /**
     * Like [buildHome], but returns the [ActivityController] so a test can drive further lifecycle
     * transitions (`recreate()`, `resume()`). Stopped at `onCreate`.
     *
     * The fake syncer is injected into the instance this returns; `recreate()` produces a new
     * activity carrying the real one, so a test that recreates and then syncs has to re-inject.
     */
    protected fun buildStandardHomeController(
        personalIdManagedLogin: Boolean = false,
        configureIntent: (Intent.() -> Unit)? = null,
    ): ActivityController<StandardHomeActivity> {
        val intent =
            Intent().apply {
                putExtra(ConnectConstants.PERSONALID_MANAGED_LOGIN, personalIdManagedLogin)
                configureIntent?.invoke(this)
            }
        val controller =
            Robolectric
                .buildActivity(StandardHomeActivity::class.java, intent)
                .create()
        ShadowLooper.idleMainLooper()
        injectFakeSyncer(controller.get())
        return controller
    }

    /** Swap in a syncer that records instead of hitting the network. */
    private fun injectFakeSyncer(home: StandardHomeActivity) = home.setFormAndDataSyncer(FormAndDataSyncerFake())

    protected fun shadowOf(activity: StandardHomeActivity): ShadowActivity = Shadows.shadowOf(activity)

    /** The labels of the buttons the home grid has rendered. Requires a [buildVisibleHome]. */
    protected fun homeButtonLabels(home: StandardHomeActivity): List<String> = homeButtonCards(home).map { labelOf(it) }

    /**
     * Tap the home-grid button labelled [label], as a user would. The click lands on the same
     * `R.id.card` the adapter bound the production listener to.
     */
    protected fun clickHomeButton(
        home: StandardHomeActivity,
        label: String,
    ) {
        val card =
            homeButtonCards(home).firstOrNull { labelOf(it) == label }
                ?: throw AssertionError("no home button labelled '$label'; rendered: ${homeButtonLabels(home)}")
        click(home, card.findViewById(R.id.card))
    }

    /**
     * Tap the sub-text strip under the button labelled [label]. Only the sync and logout cards bind
     * a listener there; it is separate from the card's own.
     */
    protected fun clickHomeButtonSubText(
        home: StandardHomeActivity,
        label: String,
    ) {
        val card =
            homeButtonCards(home).firstOrNull { labelOf(it) == label }
                ?: throw AssertionError("no home button labelled '$label'; rendered: ${homeButtonLabels(home)}")
        click(home, card.findViewById(R.id.card_subtext))
    }

    private fun click(
        home: StandardHomeActivity,
        view: View,
    ) {
        home.runOnUiThread { view.performClick() }
        ShadowLooper.idleMainLooper()
    }

    /**
     * The grid's rendered button cards, in display order. Position 0 holds the banner header rather
     * than a button, so cards are identified by carrying a label rather than by index.
     */
    private fun homeButtonCards(home: StandardHomeActivity): List<View> {
        val grid = home.findViewById<RecyclerView>(R.id.home_gridview_buttons)
        assertNotNull("home grid was never inflated", grid)
        val cards = (0 until grid.childCount).map { grid.getChildAt(it) }.filter { labelView(it) != null }
        if (cards.isEmpty()) {
            throw AssertionError("the home grid rendered no buttons; build home with buildVisibleHome()")
        }
        return cards
    }

    private fun labelOf(card: View): String = labelView(card)!!.text.toString()

    private fun labelView(card: View): TextView? = (card as? ViewGroup)?.findViewById(R.id.card_text)

    /**
     * Replace the standard session installed by [baseSetUp] with a demo-user session, so
     * `HomeScreenBaseActivity.isDemoUser()` reports true. Call before building home.
     */
    protected fun seatDemoUser() {
        CommCareApplication.instance().closeUserSession()
        DemoUserBuilder.build(
            ApplicationProvider.getApplicationContext(),
            CommCareApplication.instance().currentApp,
        )
        TestAppInstaller.login(DemoUserBuilder.DEMO_USERNAME, DemoUserBuilder.DEMO_PASSWORD)
    }

    /**
     * Give the logged-in user a PIN, so `UserKeyRecord.hasPinSet()` reports true. The record has to
     * be written back: `getUserKeyRecord()` re-reads it from SQL on every call, so mutating the
     * instance alone is discarded.
     */
    protected fun assignPinToCurrentUser(pin: String = "1234") {
        val record = CommCareApplication.instance().recordForCurrentUser
        record.assignPinToRecord(pin, TEST_PASSWORD)
        CommCareApplication
            .instance()
            .currentApp
            .getStorage(UserKeyRecord::class.java)
            .write(record)
    }

    /** Turn on the developer preference gating the launch-check PIN step and the set-pin menu item. */
    protected fun offerPinForLogin() {
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString(DeveloperPreferences.OFFER_PIN_FOR_LOGIN, PrefValues.YES)
            .apply()
    }

    /** Queue a notification so `messagesForCommCareArePending()` reports true. */
    protected fun reportPendingNotification() {
        CommCareApplication.notificationManager().reportNotificationMessage(
            NotificationMessageFactory.message(
                NotificationMessageFactory.StockMessages.Sync_NoConnections,
                "home-test-category",
            ),
        )
    }

    protected fun notificationsArePending(): Boolean = CommCareApplication.notificationManager().messagesForCommCareArePending()

    /**
     * The notifications home has reported, so a test can assert on the text the user is shown.
     * `purgeNotifications()` is the only read the manager exposes, and it drains: call once.
     */
    protected fun pendingNotifications(): List<NotificationMessage> = CommCareApplication.notificationManager().purgeNotifications()

    /** The notification manager is app-scoped, so its pending list has to be cleared per test. */
    private fun clearPendingNotifications() = CommCareApplication.notificationManager().clearNotifications(null)

    private fun clearPrefs() {
        PreferenceManager
            .getDefaultSharedPreferences(CommCareApplication.instance())
            .edit()
            .clear()
            .apply()
    }

    companion object {
        const val TEST_APP_PATH = "jr://resource/commcare-apps/form_nav_tests/profile.ccpr"
        const val TEST_USER = "test"
        const val TEST_PASSWORD = "123"
    }
}
