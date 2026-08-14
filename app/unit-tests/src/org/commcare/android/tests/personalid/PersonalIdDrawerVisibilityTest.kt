package org.commcare.android.tests.personalid

import android.os.Build
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.activities.CommCareSetupActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.StandardHomeActivity
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.util.TestAppInstaller
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectAppDatabaseUtil
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Tests that the nav drawer is shown or hidden correctly, across the three activities that can host
 * it before a CommCare session is fully up: setup, login and home.
 *
 * The rule under test lives in `BaseDrawerActivity.shouldShowDrawerAfterCheck`: the drawer appears
 * when the device is compatible (Android 9+) and — for the hosts that require it — PersonalId is
 * logged in, or unconditionally if it has ever been shown before.
 *
 * Mocking: MockK only. Mixing MockK with Mockito's static mocking in one JVM corrupts bytecode
 * instrumentation for unrelated classes later in the run, which is what this class used to do.
 * [PersonalIdManager] is not static-mocked at all — its singleton is a plain private static field,
 * so [installSpyManager] writes a spy straight into it and `getInstance()` hands that back with no
 * instrumentation involved. The spy (rather than a mock) keeps every method it doesn't stub on real
 * behaviour, which is what `checkDeviceCompability()` needs so the per-test `@Config(sdk = ...)`
 * actually decides the outcome.

 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdDrawerVisibilityTest {
    private lateinit var spyManager: PersonalIdManager
    private var realManager: PersonalIdManager? = null

    @Before
    fun setUp() {
        clearPrefs()
        (CommCareTestApplication.instance() as CommCareTestApplication).initWorkManager()
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)

        installSpyManager()

        mockkStatic(
            ConnectDatabaseHelper::class,
            ConnectJobUtils::class,
            ConnectMessagingDatabaseHelper::class,
            ConnectAppDatabaseUtil::class,
            ConnectUserDatabaseUtil::class,
        )
        every { ConnectDatabaseHelper.isDbBroken() } returns false
        every { ConnectJobUtils.getAppRecord(any(), any()) } returns null
        every { ConnectMessagingDatabaseHelper.getMessagingChannels(any()) } returns emptyList()
        every { ConnectAppDatabaseUtil.getReleaseToggles(any()) } returns emptyList()

        // The drawer header reads the signed-in user's name off getUser(). hasConnectAccess() is
        // stubbed rather than left to run its real body (which just re-reads getUser() and checks a
        // flag), because a MockK static mock throws on any method it wasn't told about, and the
        // drawer + setup + login paths all call it.
        every { ConnectUserDatabaseUtil.getUser(any()) } returns
            ConnectUserRecord("", "", "", "Test User", "", Date(), null, false, "", false)
        every { ConnectUserDatabaseUtil.hasConnectAccess(any()) } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
        restoreRealManager()
        clearPrefs()
    }

    // ======== CommCareSetupActivity ========

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `setup activity no drawer below Android 9 when logged in`() {
        setLoggedIn(true)
        val activity = Robolectric.buildActivity(CommCareSetupActivity::class.java).create().get()
        assertNull("Drawer should not be set up below Android 9", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `setup activity shows drawer on Android 9 and above when logged in`() {
        setLoggedIn(true)
        val activity = Robolectric.buildActivity(CommCareSetupActivity::class.java).create().get()
        assertNotNull("Drawer should be set up on Android 9+ when PersonalId is logged in", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `setup activity shows drawer on Android 9 and above even when not logged in`() {
        val activity = Robolectric.buildActivity(CommCareSetupActivity::class.java).create().get()
        assertNotNull("Drawer should be set up on Android 9+ regardless of PersonalId login status", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `setup activity shows drawer when previously shown regardless of Android version`() {
        // Simulate a prior session where Personal ID was logged in on Android 9+
        setLoggedIn(true)
        setDeviceCompatible(true)
        Robolectric.buildActivity(CommCareSetupActivity::class.java).create()

        // Next launch: drawer persists from prior session
        setLoggedIn(false)
        setDeviceCompatible(false)
        val activity = Robolectric.buildActivity(CommCareSetupActivity::class.java).create().get()
        assertNotNull("Drawer should be set up if previously shown, regardless of Android version", activity.drawerAdapter)
    }

    // ======== LoginActivity ========

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `login activity no drawer below Android 9 when logged in`() {
        setLoggedIn(true)
        val activity = Robolectric.buildActivity(LoginActivity::class.java).create().get()
        assertNull("Drawer should not be set up below Android 9", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `login activity shows drawer on Android 9 and above when logged in`() {
        setLoggedIn(true)
        val activity = Robolectric.buildActivity(LoginActivity::class.java).create().get()
        assertNotNull("Drawer should be set up on Android 9+ when PersonalId is logged in", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `login activity no drawer when not logged in`() {
        val activity = Robolectric.buildActivity(LoginActivity::class.java).create().get()
        assertNull("Drawer should not be set up when PersonalId is not logged in", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `login activity shows drawer when previously shown regardless of Android version`() {
        // Simulate a prior session where Personal ID was logged in on Android 9+.
        setLoggedIn(true)
        setDeviceCompatible(true)
        Robolectric.buildActivity(LoginActivity::class.java).create()

        // Next launch: drawer persists from prior session
        setLoggedIn(false)
        setDeviceCompatible(false)
        val activity = Robolectric.buildActivity(LoginActivity::class.java).create().get()
        assertNotNull("Drawer should be set up if previously shown, regardless of Android version", activity.drawerAdapter)
    }

    // ======== StandardHomeActivity ========

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `home activity no drawer below Android 9 when logged in`() {
        setLoggedIn(true)
        val activity = Robolectric.buildActivity(StandardHomeActivity::class.java).create().get()
        assertNull("Drawer should not be set up below Android 9", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `home activity shows drawer on Android 9 and above when logged in`() {
        setLoggedIn(true)
        val activity = Robolectric.buildActivity(StandardHomeActivity::class.java).create().get()
        assertNotNull("Drawer should be set up on Android 9+ when PersonalId is logged in", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `home activity no drawer when not logged in`() {
        val activity = Robolectric.buildActivity(StandardHomeActivity::class.java).create().get()
        assertNull("Drawer should not be set up when PersonalId is not logged in", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `home activity shows drawer when previously shown regardless of Android version`() {
        // Simulate a prior session where Personal ID was logged in on Android 9+.
        setLoggedIn(true)
        setDeviceCompatible(true)
        Robolectric.buildActivity(StandardHomeActivity::class.java).create()

        // Next launch: drawer persists from prior session
        setLoggedIn(false)
        setDeviceCompatible(false)
        val activity = Robolectric.buildActivity(StandardHomeActivity::class.java).create().get()
        assertNotNull("Drawer should be set up if previously shown, regardless of Android version", activity.drawerAdapter)
    }

    // ======== Helpers ========

    /**
     * Returns the adapter on [R.id.nav_drawer_recycler] if the drawer was set up,
     * or `null` if the drawer controller was never initialized.
     */
    private val android.app.Activity.drawerAdapter: RecyclerView.Adapter<*>?
        get() = findViewById<RecyclerView?>(R.id.nav_drawer_recycler)?.adapter

    private fun setLoggedIn(loggedIn: Boolean) {
        every { spyManager.isloggedIn() } returns loggedIn
    }

    /**
     * Pin `checkDeviceCompability()` to [compatible] instead of letting the spy run its real
     * `SDK_INT >= P` check. Only needed by the "previously shown" rows, where the first launch has to
     * disagree with the `@Config(sdk = ...)` the assertion itself runs under.
     */
    private fun setDeviceCompatible(compatible: Boolean) {
        every { spyManager.checkDeviceCompability() } returns compatible
    }

    /**
     * Seat a spy on the [PersonalIdManager] singleton. `getInstance()` just returns the private
     * static `manager` field, so writing the spy there is enough — no static mocking needed.
     * `init()` is stubbed to a no-op because its real body reads the Connect DB.
     */
    private fun installSpyManager() {
        realManager = PersonalIdManager.getInstance()
        spyManager = spyk(realManager!!)
        every { spyManager.init(any()) } just Runs
        setLoggedIn(false)
        singletonField().set(null, spyManager)
    }

    /**
     * Put the real singleton back. Without this the spy outlives the test and every later test in
     * this sandbox would see stubbed login state — the same kind of leak this class was cleaned up
     * to avoid.
     */
    private fun restoreRealManager() {
        singletonField().set(null, realManager)
    }

    private fun singletonField() =
        PersonalIdManager::class.java
            .getDeclaredField("manager")
            .apply { isAccessible = true }

    private fun clearPrefs() {
        PreferenceManager
            .getDefaultSharedPreferences(CommCareApplication.instance())
            .edit()
            .clear()
            .apply()
    }

    companion object {
        private const val TEST_APP_PATH = "jr://resource/commcare-apps/form_nav_tests/profile.ccpr"
        private const val TEST_USER = "test"
        private const val TEST_PASSWORD = "123"
    }
}
