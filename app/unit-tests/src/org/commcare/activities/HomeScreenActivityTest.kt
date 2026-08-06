package org.commcare.activities

import android.content.Intent
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.android.database.app.models.UserKeyRecord
import org.commcare.android.mocks.FormAndDataSyncerFake
import org.commcare.android.util.TestAppInstaller
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ConnectNavHelper
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
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
import java.lang.reflect.Field

/**
 * Shared base for home-screen characterization tests. Installs a test app + user, static-mocks the
 * Connect statics the home screen boots through (defaulting to "no Connect job / nothing to show")
 * and [ConnectivityStatus] (defaulting to "online, not in airplane mode"), and exposes helpers to
 * build the home activity.
 *
 * Direct subclasses of this class cover *traditional* home behaviour — everything that isn't
 * PersonalId/Connect. The Connect stubbing here is boot scaffolding, not a subject: the home screen
 * cannot be created without it, and pinning it to "no Connect" is what keeps the traditional tests
 * deterministic and offline. Tests that actually assert on Connect behaviour belong in the
 * `HomeConnect*` classes, which extend [HomeConnectTestBase] for the job/tile fixtures.
 *
 * Subclasses run under [AndroidJUnit4] with [CommCareTestApplication]; add `@Config(sdk = [...])`
 * on individual tests only when a specific Android level matters.
 */
@Config(application = CommCareTestApplication::class, shadows = [ShadowHomeTestSandbox::class])
@RunWith(AndroidJUnit4::class)
abstract class HomeScreenActivityTest {
    @Before
    fun baseSetUp() {
        clearPrefs()
        clearPendingNotifications()
        (CommCareTestApplication.instance() as CommCareTestApplication).initWorkManager()
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)

        // Default to "online, not in airplane mode" so the sync paths don't branch into the
        // offline handling unless a test asks for it. Mocked with MockK rather than Mockito: mixing
        // the two inline mock makers in the same JVM corrupts bytecode instrumentation for
        // unrelated classes later in the run.
        mockkStatic(ConnectivityStatus::class)
        every { ConnectivityStatus.isNetworkAvailable(any()) } returns true
        every { ConnectivityStatus.isAirplaneModeOn(any()) } returns false

        // Stub outbound Connect navigation so we can verify it without real navigation/unlock.
        mockkObject(ConnectNavHelper)
        every { ConnectNavHelper.goToConnectJobsList(any(), any()) } returns Unit
        every { ConnectNavHelper.goToMessaging(any(), any()) } returns Unit
        every { ConnectNavHelper.goToWorkHistory(any()) } returns Unit
        every { ConnectNavHelper.goToActiveInfoForJob(any(), any(), any()) } returns Unit
        every { ConnectNavHelper.unlockAndGoToConnectJobsList(any(), any(), any()) } returns Unit
        every { ConnectNavHelper.unlockAndGoToMessaging(any(), any(), any(), any()) } returns Unit
        every { ConnectNavHelper.unlockAndGoToWorkHistory(any(), any(), any()) } returns Unit
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
     * transitions (notably `recreate()` for save/restore round-trips). Stopped at `onCreate`.
     *
     * Note: the fake syncer is injected into the instance this returns. `recreate()` produces a
     * *new* activity carrying the real [org.commcare.activities.FormAndDataSyncer], so a test that
     * recreates and then drives a sync path must call [injectFakeSyncer] on the new instance. The
     * current recreate tests don't reach a sync, which is why they get away without it.
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
     * Give the logged-in user a PIN, so `UserKeyRecord.hasPinSet()` reports true.
     *
     * The record must be written back to storage: `CommCareSessionService.getUserKeyRecord()` re-reads
     * it from SQL on every call rather than holding it in memory, so mutating the instance alone is
     * discarded. This mirrors what `CreatePinActivity` does after assigning a PIN.
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

    /**
     * Turn on the "offer a PIN at login" developer preference, which gates both the launch-check PIN
     * step and the `action_set_pin` options-menu item.
     */
    protected fun offerPinForLogin() {
        CommCareApplication
            .instance()
            .currentApp.appPreferences
            .edit()
            .putString(DeveloperPreferences.OFFER_PIN_FOR_LOGIN, PrefValues.YES)
            .apply()
    }

    /**
     * Queue a CommCare notification so `messagesForCommCareArePending()` reports true. The category
     * is arbitrary — the reader only counts pending messages.
     */
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
     * The notification manager is app-scoped and its pending list outlives an individual test, so
     * clear it around every test — otherwise a test that reports a notification silently changes
     * the answer `messagesForCommCareArePending()` gives to whichever test runs next.
     */
    private fun clearPendingNotifications() = CommCareApplication.notificationManager().clearNotifications(null)

    /**
     * Read a private field by name, walking up the class hierarchy. Several launch/nav flags on the
     * home activity's base classes have no accessors; these helpers let a characterization test pin
     * their save/restore contract until CCCT-2679/2683 move them behind the coordinator's
     * SavedStateProvider (at which point real seams replace this reflection).
     */
    protected fun readField(
        target: Any,
        name: String,
    ): Any? = fieldFor(target.javaClass, name).get(target)

    protected fun writeField(
        target: Any,
        name: String,
        value: Any?,
    ) = fieldFor(target.javaClass, name).set(target, value)

    private fun fieldFor(
        start: Class<*>,
        name: String,
    ): Field {
        var cls: Class<*>? = start
        while (cls != null) {
            try {
                return cls.getDeclaredField(name).apply { isAccessible = true }
            } catch (e: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException("$name not found on ${start.name} or its superclasses")
    }

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
