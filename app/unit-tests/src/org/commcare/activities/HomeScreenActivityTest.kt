package org.commcare.activities

import android.content.Intent
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
import org.commcare.views.notifications.NotificationMessageFactory
import org.junit.After
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
 * other dependency, Connect DB included, runs for real. An empty Connect DB is what makes a direct
 * subclass boot with no job seated. Tests that assert on Connect behaviour extend
 * [HomeConnectTestBase], which seats that state in the DB.
 *
 * Subclasses run under [AndroidJUnit4] with [CommCareTestApplication]; add `@Config(sdk = [...])`
 * on individual tests only when a specific Android level matters.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
abstract class HomeScreenActivityTest {
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
     * Like [buildHome], but returns the [ActivityController] so a test can drive further lifecycle
     * transitions (notably `recreate()`). Stopped at `onCreate`.
     *
     * The fake syncer is injected into the instance this returns; `recreate()` produces a new
     * activity carrying the real one, so a test that recreates and then syncs must call
     * [injectFakeSyncer] again.
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
    protected fun injectFakeSyncer(home: StandardHomeActivity) = home.setFormAndDataSyncer(FormAndDataSyncerFake())

    protected fun shadowOf(activity: StandardHomeActivity): ShadowActivity = Shadows.shadowOf(activity)

    protected fun uiController(home: StandardHomeActivity): StandardHomeActivityUIController =
        home.getUIController() as StandardHomeActivityUIController

    /**
     * The labels of the buttons the home grid renders, read off the bound card views. Position 0 is
     * the banner header rather than a button, so it is skipped.
     */
    protected fun homeButtonLabels(home: StandardHomeActivity): List<String> {
        val grid = home.findViewById<RecyclerView>(R.id.home_gridview_buttons)
        val adapter = grid.adapter!!
        return (1 until adapter.itemCount).map { position ->
            val holder = adapter.createViewHolder(grid, adapter.getItemViewType(position))
            adapter.bindViewHolder(holder, position)
            holder.itemView
                .findViewById<TextView>(R.id.card_text)
                .text
                .toString()
        }
    }

    /** The uniqueId of the currently seated CommCare app. */
    protected fun seatedAppId(): String = CommCareApplication.instance().currentApp.uniqueId

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
