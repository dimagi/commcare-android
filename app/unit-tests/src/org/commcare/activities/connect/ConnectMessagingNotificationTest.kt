package org.commcare.activities.connect

import android.content.Context
import android.content.Intent
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.activities.DispatchActivity
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord
import org.commcare.android.util.ActivityAssertions
import org.commcare.android.util.ConnectTestUtils.signInToPersonalId
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.connect.network.personalId.parser.NotificationTestUtil
import org.commcare.dalvik.R
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.utils.coroutines.DispatcherProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Date

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockApi = PersonalIdMockApiServer()
    private lateinit var savedPersonalIdStatus: PersonalIdSessionManager.PersonalIdStatus

    @Before
    fun setUp() {
        mockApi.start()
        repeat(SYNC_RESPONSES) {
            mockApi.server.enqueue(MockResponse().setBody("""{"notifications": [], "channels": []}"""))
        }
        savedPersonalIdStatus = PersonalIdSessionManager.getInstance().status
        signInToPersonalId(userId = "test-user", password = "test-password")
        storeConsentedChannel()
        mockkObject(PersonalIdUnlocker)
        setUnlockResult(true)
        mockkObject(DispatcherProvider)
        every { DispatcherProvider.io() } returns UnconfinedTestDispatcher()
    }

    @After
    fun tearDown() {
        unmockkAll()
        mockApi.shutdown()
        ConnectDatabaseHelper.teardown()
        PersonalIdSessionManager.getInstance().status = savedPersonalIdStatus
    }

    @Test
    fun `a notification tapped outside the app opens the chat above the channel list`() {
        val activity = launchMessagingNotification(openedFromNotificationOrInvite = true)

        val navController = getNavController(activity)
        assertEquals(R.id.connectMessageFragment, navController.currentDestination?.id)
        assertEquals(R.id.channelListFragment, navController.previousBackStackEntry?.destination?.id)
    }

    @Test
    fun `recreating a chat opened from a notification keeps one chat above the channel list`() {
        val controller = buildMessagingNotificationController(openedFromNotificationOrInvite = true)

        controller.recreate()
        ShadowLooper.idleMainLooper()

        val navController = getNavController(controller.get())
        assertEquals(R.id.connectMessageFragment, navController.currentDestination?.id)
        assertEquals(R.id.channelListFragment, navController.previousBackStackEntry?.destination?.id)
        navController.popBackStack()
        assertNull(navController.previousBackStackEntry)
    }

    @Test
    fun `a notification opened inside the app opens the chat alone`() {
        val activity = launchMessagingNotification(openedFromNotificationOrInvite = false)

        val navController = getNavController(activity)
        assertEquals(R.id.connectMessageFragment, navController.currentDestination?.id)
        assertNull(navController.previousBackStackEntry)
    }

    @Test
    fun `a declined unlock on a notification tapped outside the app restarts routing as the only screen in the task`() {
        setUnlockResult(false)

        val activity = launchMessagingNotification(openedFromNotificationOrInvite = true)

        val dispatch = ActivityAssertions.assertStarted(activity, DispatchActivity::class.java)
        assertTrue(dispatch.getBooleanExtra(DispatchActivity.EXTRA_PERSONAL_ID_UNLOCK_DECLINED, false))
        val taskRootFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        assertEquals(taskRootFlags, dispatch.flags and taskRootFlags)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a declined unlock on a notification opened inside the app just closes Messaging`() {
        setUnlockResult(false)

        val activity = launchMessagingNotification(openedFromNotificationOrInvite = false)

        ActivityAssertions.assertStartedNothing(activity)
        assertTrue(activity.isFinishing)
    }

    private fun setUnlockResult(success: Boolean) {
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<ConnectActivityCompleteListener>().connectActivityComplete(success, null)
        }
    }

    private fun storeConsentedChannel() {
        val channel =
            ConnectMessagingChannelRecord().apply {
                channelId = CHANNEL_ID
                channelCreated = Date()
                consented = true
                channelSource = ""
                keyUrl = ""
                key = NotificationTestUtil.TEST_ENCRYPTION_KEY
                channelName = "Site team"
            }
        ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel)
    }

    private fun launchMessagingNotification(openedFromNotificationOrInvite: Boolean): ConnectMessagingActivity =
        buildMessagingNotificationController(openedFromNotificationOrInvite).get()

    private fun buildMessagingNotificationController(
        openedFromNotificationOrInvite: Boolean,
    ): ActivityController<ConnectMessagingActivity> {
        val intent =
            Intent(context, ConnectMessagingActivity::class.java)
                .putExtra(ConnectConstants.REDIRECT_ACTION, ConnectConstants.CCC_MESSAGE)
                .putExtra(ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID, CHANNEL_ID)
                .putExtra(ConnectConstants.OPENED_FROM_NOTIFICATION_OR_INVITE, openedFromNotificationOrInvite)
        val controller = Robolectric.buildActivity(ConnectMessagingActivity::class.java, intent).setup()
        mockApi.drainHttp()
        ShadowLooper.idleMainLooper()
        return controller
    }

    private fun getNavController(activity: ConnectMessagingActivity): NavController =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect_messaging) as NavHostFragment)
            .navController

    private companion object {
        const val CHANNEL_ID = "channel-1"
        const val SYNC_RESPONSES = 3
    }
}
