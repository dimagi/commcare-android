package org.commcare.android.tests.personalid

import android.os.Build
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkStatic
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
import org.mockito.MockedStatic
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Tests that the nav drawer is shown or hidden correctly
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdDrawerVisibilityTest {
    private lateinit var spyManager: PersonalIdManager
    private lateinit var mockedPersonalIdManager: MockedStatic<PersonalIdManager>
    private lateinit var mockedConnectUserDb: MockedStatic<ConnectUserDatabaseUtil>

    @Before
    fun setUp() {
        clearPrefs()
        (CommCareTestApplication.instance() as CommCareTestApplication).initWorkManager()
        TestAppInstaller.installAppAndLogin(TEST_APP_PATH, TEST_USER, TEST_PASSWORD)

        spyManager = spy(PersonalIdManager.getInstance())
        doNothing().whenever(spyManager).init(any())
        doReturn(false).whenever(spyManager).isloggedIn()

        mockedPersonalIdManager = mockStatic(PersonalIdManager::class.java, CALLS_REAL_METHODS)
        mockedPersonalIdManager.`when`<PersonalIdManager> { PersonalIdManager.getInstance() }.thenReturn(spyManager)

        mockkStatic(
            ConnectDatabaseHelper::class,
            ConnectJobUtils::class,
            ConnectMessagingDatabaseHelper::class,
            ConnectAppDatabaseUtil::class,
        )
        every { ConnectDatabaseHelper.isDbBroken() } returns false
        every { ConnectJobUtils.getAppRecord(any(), any()) } returns null
        every { ConnectMessagingDatabaseHelper.getMessagingChannels(any()) } returns emptyList()
        every { ConnectAppDatabaseUtil.getReleaseToggles(any()) } returns emptyList()

        mockedConnectUserDb = mockStatic(ConnectUserDatabaseUtil::class.java, CALLS_REAL_METHODS)
        mockedConnectUserDb
            .`when`<ConnectUserRecord> { ConnectUserDatabaseUtil.getUser(any()) }
            .thenReturn(ConnectUserRecord("", "", "", "Test User", "", Date(), null, false, "", false))
    }

    @After
    fun tearDown() {
        mockedPersonalIdManager.close()
        mockedConnectUserDb.close()
        unmockkAll()
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
    fun `setup activity no drawer when not logged in`() {
        val activity = Robolectric.buildActivity(CommCareSetupActivity::class.java).create().get()
        assertNull("Drawer should not be set up when PersonalId is not logged in", activity.drawerAdapter)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `setup activity shows drawer when previously shown regardless of Android version`() {
        // Simulate a prior session where Personal ID was logged in on Android 9+
        setLoggedIn(true)
        doReturn(true).whenever(spyManager).checkDeviceCompability()
        Robolectric.buildActivity(CommCareSetupActivity::class.java).create()

        // Next launch: drawer persists from prior session
        setLoggedIn(false)
        doReturn(false).whenever(spyManager).checkDeviceCompability()
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
        doReturn(true).whenever(spyManager).checkDeviceCompability()
        Robolectric.buildActivity(LoginActivity::class.java).create()

        // Next launch: drawer persists from prior session
        setLoggedIn(false)
        doReturn(false).whenever(spyManager).checkDeviceCompability()
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
        doReturn(true).whenever(spyManager).checkDeviceCompability()
        Robolectric.buildActivity(StandardHomeActivity::class.java).create()

        // Next launch: drawer persists from prior session
        setLoggedIn(false)
        doReturn(false).whenever(spyManager).checkDeviceCompability()
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
        doReturn(loggedIn).whenever(spyManager).isloggedIn()
    }

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
