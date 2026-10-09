package org.commcare.fragments.connectMessaging

import android.content.Context
import android.view.Menu
import android.widget.RadioButton
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.android.util.ConnectTestUtils.signInToPersonalId
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.messaging.ConnectMessagingAttachmentDownloadScheduler
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.dalvik.R
import org.commcare.preferences.ConnectMessagingPreferences
import org.commcare.preferences.ConnectMessagingPreferences.AttachmentAutoDownload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectMessagingSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockApi = PersonalIdMockApiServer()
    private lateinit var activity: ConnectMessagingActivity
    private lateinit var savedPersonalIdStatus: PersonalIdManager.PersonalIdStatus

    @Before
    fun setUp() {
        mockApi.start()
        repeat(SYNC_RESPONSES) {
            mockApi.server.enqueue(MockResponse().setBody("""{"notifications": [], "channels": []}"""))
        }
        savedPersonalIdStatus = PersonalIdManager.getInstance().status
        signInToPersonalId(userId = "test-user", password = "test-password")
    }

    @After
    fun tearDown() {
        mockApi.shutdown()
        ConnectMessagingAttachmentDownloadScheduler.cancelDownloads(context)
        ConnectMessagingPreferences.clear(context)
        ConnectDatabaseHelper.teardown()
        PersonalIdManager.getInstance().status = savedPersonalIdStatus
    }

    @Test
    fun `settings in the channel list menu opens messaging settings`() {
        launchChannelList()

        val settingsItem = shadowOf(activity).optionsMenu.findItem(Menu.FIRST)
        assertEquals(context.getString(R.string.menu_settings), settingsItem.title.toString())
        shadowOf(activity).clickMenuItem(Menu.FIRST)
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.connectMessagingSettingsFragment, navController().currentDestination?.id)
        assertEquals(context.getString(R.string.connect_messaging_settings_title), activity.title.toString())
    }

    @Test
    fun `settings screen starts on wifi and mobile data`() {
        openSettings()

        assertTrue(radioButton(R.id.rbAnyNetwork).isChecked)
    }

    @Test
    fun `settings screen shows a saved choice`() {
        ConnectMessagingPreferences.setAttachmentAutoDownload(context, AttachmentAutoDownload.MANUAL_ONLY)

        openSettings()

        assertTrue(radioButton(R.id.rbManualOnly).isChecked)
    }

    @Test
    fun `choosing large attachments on wifi only holds large automatic downloads for wifi`() {
        openSettings()

        radioButton(R.id.rbLargeOnWifiOnly).performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(AttachmentAutoDownload.LARGE_ON_WIFI_ONLY, ConnectMessagingPreferences.getAttachmentAutoDownload(context))
        assertEquals(
            NetworkType.UNMETERED,
            pendingWork(ConnectMessagingAttachmentDownloadScheduler.LARGE_FILES_WORK_NAME).single().constraints.requiredNetworkType,
        )
    }

    @Test
    fun `choosing manual download only stops automatic downloads`() {
        openSettings()

        radioButton(R.id.rbManualOnly).performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(AttachmentAutoDownload.MANUAL_ONLY, ConnectMessagingPreferences.getAttachmentAutoDownload(context))
        assertTrue(pendingWork(ConnectMessagingAttachmentDownloadScheduler.ALL_FILES_WORK_NAME).isEmpty())
        assertTrue(pendingWork(ConnectMessagingAttachmentDownloadScheduler.SMALL_FILES_WORK_NAME).isEmpty())
        assertTrue(pendingWork(ConnectMessagingAttachmentDownloadScheduler.LARGE_FILES_WORK_NAME).isEmpty())
    }

    private fun launchChannelList() {
        activity = Robolectric.buildActivity(ConnectMessagingActivity::class.java).setup().get()
        mockApi.drainHttp()
    }

    private fun openSettings() {
        launchChannelList()
        navController().navigate(R.id.connectMessagingSettingsFragment)
        ShadowLooper.idleMainLooper()
    }

    private fun radioButton(id: Int): RadioButton = activity.findViewById(id)

    private fun pendingWork(workName: String): List<WorkInfo> =
        WorkManager
            .getInstance(context)
            .getWorkInfosForUniqueWork(workName)
            .get()
            .filter { !it.state.isFinished }

    private fun navController() =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect_messaging) as NavHostFragment)
            .navController

    private companion object {
        const val SYNC_RESPONSES = 3
    }
}
