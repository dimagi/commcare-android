package org.commcare.navdrawer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import org.commcare.CommCareApplication
import org.commcare.activities.BaseHomeScreenActivityTest
import org.commcare.activities.CommCareActivity
import org.commcare.activities.DispatchActivity
import org.commcare.activities.LoginActivity
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.activities.connect.PersonalIdWorkHistoryActivity
import org.commcare.activities.connect.viewmodel.PersonalIdWorkHistoryViewModel
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.util.ActivityAssertions.assertStarted
import org.commcare.android.util.ActivityAssertions.assertStartedNothing
import org.commcare.android.util.ConnectTestUtils.signInToPersonalId
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.dalvik.R
import org.commcare.personalId.PersonalIdUnlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Date

@Config(sdk = [Build.VERSION_CODES.P])
class SidebarSectionNavigationTest : BaseHomeScreenActivityTest() {
    @Before
    fun signInAndStubDeviceAndNetwork() {
        signInToPersonalId(hasConnectAccess = true)
        mockkObject(PersonalIdUnlocker)
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<PersonalIdManager.ConnectActivityCompleteListener>().connectActivityComplete(true)
        }
        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } just Runs
        every { MessageManager.sendUnsentMessages(any()) } just Runs
        mockkConstructor(PersonalIdWorkHistoryViewModel::class)
        every { anyConstructed<PersonalIdWorkHistoryViewModel>().retrieveAndProcessWorkHistory(any()) } just Runs
    }

    @Test
    fun `work history shows the sidebar when signed in to PersonalID`() {
        assertNotNull(getDrawerAdapter(buildWorkHistoryScreen()))
    }

    @Test
    fun `messaging shows the sidebar when signed in to PersonalID`() {
        assertNotNull(getDrawerAdapter(buildMessagingScreen()))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `work history has no sidebar below Android 9`() {
        assertNull(getDrawerAdapter(buildWorkHistoryScreen()))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `messaging has no sidebar below Android 9`() {
        assertNull(getDrawerAdapter(buildMessagingScreen()))
    }

    @Test
    fun `messaging closes without a sidebar when signed out of PersonalID`() {
        PersonalIdManager.getInstance().forgetUser("sidebar test")

        val messagingScreen = buildMessagingScreen()

        assertNull(getDrawerAdapter(messagingScreen))
        assertTrue(messagingScreen.isFinishing)
    }

    @Test
    fun `opening messaging from work history replaces work history`() {
        val workHistoryScreen = buildWorkHistoryScreen()

        clickDrawerItem(workHistoryScreen, R.string.connect_messaging_title)

        assertStarted(workHistoryScreen, ConnectMessagingActivity::class.java)
        assertTrue(workHistoryScreen.isFinishing)
    }

    @Test
    fun `opening work history from messaging replaces messaging`() {
        val messagingScreen = buildMessagingScreen()

        clickDrawerItem(messagingScreen, R.string.personalid_work_history)

        assertStarted(messagingScreen, PersonalIdWorkHistoryActivity::class.java)
        assertTrue(messagingScreen.isFinishing)
    }

    @Test
    fun `opening opportunities from messaging replaces messaging`() {
        val messagingScreen = buildMessagingScreen()

        clickDrawerItem(messagingScreen, R.string.nav_drawer_opportunities)

        assertStarted(messagingScreen, ConnectActivity::class.java)
        assertTrue(messagingScreen.isFinishing)
    }

    @Test
    fun `tapping the open section only closes the sidebar`() {
        val workHistoryScreen = buildWorkHistoryScreen()

        clickDrawerItem(workHistoryScreen, R.string.personalid_work_history)

        assertStartedNothing(workHistoryScreen)
        assertFalse(workHistoryScreen.isFinishing)
    }

    @Test
    fun `commcare apps asks before logging out of the active app`() {
        val workHistoryScreen = buildWorkHistoryScreen()

        clickDrawerItem(workHistoryScreen, R.string.nav_drawer_commcare_apps)

        assertNotNull(workHistoryScreen.currentAlertDialog)
        assertStartedNothing(workHistoryScreen)
        assertTrue(CommCareApplication.isSessionActive())
    }

    @Test
    fun `confirming the commcare apps prompt logs out and opens the login page`() {
        val workHistoryScreen = buildWorkHistoryScreen()
        clickDrawerItem(workHistoryScreen, R.string.nav_drawer_commcare_apps)

        clickDialogButton(workHistoryScreen, R.id.positive_button)

        assertFalse(CommCareApplication.isSessionActive())
        assertOpenedLoginAsTaskRoot(workHistoryScreen)
    }

    @Test
    fun `cancelling the commcare apps prompt keeps the user in the section`() {
        val workHistoryScreen = buildWorkHistoryScreen()
        clickDrawerItem(workHistoryScreen, R.string.nav_drawer_commcare_apps)

        clickDialogButton(workHistoryScreen, R.id.negative_button)

        assertTrue(CommCareApplication.isSessionActive())
        assertStartedNothing(workHistoryScreen)
        assertFalse(workHistoryScreen.isFinishing)
    }

    @Test
    fun `commcare apps skips the prompt when no app is logged in`() {
        CommCareApplication.instance().closeUserSession()
        val messagingScreen = buildMessagingScreen()

        clickDrawerItem(messagingScreen, R.string.nav_drawer_commcare_apps)

        assertNull(messagingScreen.currentAlertDialog)
        assertOpenedLoginAsTaskRoot(messagingScreen)
    }

    @Test
    fun `the channel list shows the sidebar icon and leaves the sidebar unlocked`() {
        val messagingScreen = buildMessagingScreen()

        assertEquals(0f, getNavigationArrowProgress(messagingScreen))
        assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, getDrawerLockMode(messagingScreen))
    }

    @Test
    fun `a chat shows back and locks the sidebar closed`() {
        storeChannel()
        val messagingScreen = buildMessagingScreen()

        openChat(messagingScreen)

        assertEquals(1f, getNavigationArrowProgress(messagingScreen))
        assertEquals(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getDrawerLockMode(messagingScreen))
    }

    @Test
    fun `back from a chat returns to the channel list with the sidebar`() {
        storeChannel()
        val messagingScreen = buildMessagingScreen()
        openChat(messagingScreen)

        getNavigationButton(messagingScreen).performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.channelListFragment, getNavController(messagingScreen).currentDestination?.id)
        assertEquals(0f, getNavigationArrowProgress(messagingScreen))
        assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, getDrawerLockMode(messagingScreen))
    }

    @Test
    fun `a chat opened directly locks the sidebar closed`() {
        storeChannel()

        val messagingScreen =
            buildMessagingScreen {
                putExtra(ConnectMessagingActivity.CHANNEL_ID, CHANNEL_ID)
            }

        assertEquals(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getDrawerLockMode(messagingScreen))
    }

    private fun buildWorkHistoryScreen(): PersonalIdWorkHistoryActivity = launchActivity(PersonalIdWorkHistoryActivity::class.java)

    private fun buildMessagingScreen(configureIntent: (Intent.() -> Unit)? = null): ConnectMessagingActivity =
        launchActivity(ConnectMessagingActivity::class.java, configureIntent)

    private fun <T : Activity> launchActivity(
        clazz: Class<T>,
        configureIntent: (Intent.() -> Unit)? = null,
    ): T {
        val intent = Intent().apply { configureIntent?.invoke(this) }
        return Robolectric
            .buildActivity(clazz, intent)
            .setup()
            .get()
            .also { ShadowLooper.idleMainLooper() }
    }

    private fun getDrawerAdapter(activity: Activity): RecyclerView.Adapter<*>? =
        activity.findViewById<RecyclerView?>(R.id.nav_drawer_recycler)?.adapter

    private fun getDrawerLayout(activity: Activity): DrawerLayout = activity.findViewById(R.id.drawer_layout)

    private fun getDrawerLockMode(activity: Activity): Int = getDrawerLayout(activity).getDrawerLockMode(GravityCompat.START)

    private fun getNavigationArrowProgress(activity: Activity): Float =
        (activity.findViewById<Toolbar>(R.id.toolbar).navigationIcon as DrawerArrowDrawable).progress

    private fun getNavigationButton(activity: Activity): ImageButton {
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        return toolbar.children.filterIsInstance<ImageButton>().first { it.drawable === toolbar.navigationIcon }
    }

    private fun getNavController(messaging: ConnectMessagingActivity): NavController =
        (messaging.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect_messaging) as NavHostFragment)
            .navController

    private fun openChat(messaging: ConnectMessagingActivity) {
        getNavController(messaging).navigate(
            R.id.connectMessageFragment,
            bundleOf(ConnectMessagingActivity.CHANNEL_ID to CHANNEL_ID),
        )
        ShadowLooper.idleMainLooper()
    }

    private fun clickDialogButton(
        activity: CommCareActivity<*>,
        buttonId: Int,
    ) {
        activity.currentAlertDialog!!
            .dialog!!
            .findViewById<Button>(buttonId)
            .performClick()
        ShadowLooper.idleMainLooper()
    }

    private fun clickDrawerItem(
        activity: CommCareActivity<*>,
        titleRes: Int,
    ) {
        val title = activity.getString(titleRes)
        val recycler = activity.findViewById<RecyclerView>(R.id.nav_drawer_recycler)
        val item =
            recycler.children.firstOrNull { it.findViewById<TextView>(R.id.list_title).text == title }
                ?: throw AssertionError("no sidebar item titled '$title'")
        item.performClick()
        activity.supportFragmentManager.executePendingTransactions()
        ShadowLooper.idleMainLooper()
    }

    private fun assertOpenedLoginAsTaskRoot(from: Activity) {
        val intent = assertStarted(from, DispatchActivity::class.java)
        assertTrue(intent.getBooleanExtra(LoginActivity.USER_TRIGGERED_LOGOUT, false))
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            intent.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        assertTrue(from.isFinishing)
    }

    private fun storeChannel() {
        val channel =
            ConnectMessagingChannelRecord().apply {
                channelId = CHANNEL_ID
                channelCreated = Date()
                answeredConsent = true
                consented = true
                channelSource = "Test Source"
                keyUrl = "https://example.org/key"
                key = "test-key"
                channelName = "Test Channel"
            }
        ConnectMessagingDatabaseHelper.storeMessagingChannel(CommCareApplication.instance(), channel)
    }

    companion object {
        private const val CHANNEL_ID = "test-channel-id"
    }
}
