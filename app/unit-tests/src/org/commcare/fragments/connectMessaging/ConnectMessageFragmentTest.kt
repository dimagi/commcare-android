package org.commcare.fragments.connectMessaging

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.PersonalIdSessionManager
import org.commcare.connect.database.ConnectMessagingDatabaseHelper
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.connect.network.personalId.PersonalIdApiEndpoints
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUnlocker
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectMessageFragmentTest {
    private val channelId = "test-channel"
    private val mockApiServer = PersonalIdMockApiServer(PersonalIdMockApiServer.CallbackMode.MAIN_LOOPER)
    private val sendResponseRelease = CountDownLatch(1)
    private lateinit var savedStatus: PersonalIdSessionManager.PersonalIdStatus

    @Before
    fun setUp() {
        val sessionManager = PersonalIdSessionManager.getInstance()
        savedStatus = sessionManager.status
        sessionManager.status = PersonalIdSessionManager.PersonalIdStatus.LoggedIn
        ConnectTestUtils.signInToPersonalId(hasConnectAccess = true, userId = "user", password = "password")
        storeConsentedChannel()

        mockkObject(PersonalIdUnlocker)
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<ConnectActivityCompleteListener>().connectActivityComplete(true, null)
        }

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportPersonalIDMessageSent() } returns Unit
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) = Unit
            }

        mockApiServer.start()
    }

    @After
    fun tearDown() {
        sendResponseRelease.countDown()
        mockApiServer.shutdown()
        PersonalIdSessionManager.getInstance().status = savedStatus
        unmockkAll()
    }

    @Test
    fun `a failed send that returns after leaving the chat does not crash`() {
        respondToSendWith(500)

        val crash = runCatching { sendMessageThenLeaveBeforeServerReplies() }.exceptionOrNull()

        assertNull(crash)
        verify(exactly = 0) { FirebaseAnalyticsUtil.reportPersonalIDMessageSent() }
    }

    @Test
    fun `a successful send that returns after leaving the chat is still reported`() {
        respondToSendWith(200)

        sendMessageThenLeaveBeforeServerReplies()

        verify(exactly = 1) { FirebaseAnalyticsUtil.reportPersonalIDMessageSent() }
    }

    private fun storeConsentedChannel() {
        val channelRecord =
            ConnectMessagingChannelRecord.fromJson(
                JSONObject().apply {
                    put(ConnectMessagingChannelRecord.META_CHANNEL_ID, channelId)
                    put(ConnectMessagingChannelRecord.META_CONSENT, true)
                    put(ConnectMessagingChannelRecord.META_CHANNEL_SOURCE, "Test Org")
                    put(ConnectMessagingChannelRecord.META_KEY_URL, "https://example.org/key")
                },
            )
        channelRecord.answeredConsent = true
        channelRecord.key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        ConnectMessagingDatabaseHelper.storeMessagingChannel(
            ApplicationProvider.getApplicationContext(),
            channelRecord,
        )
    }

    private fun respondToSendWith(sendResponseCode: Int) {
        mockApiServer.server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (isSendRequest(request)) {
                        sendResponseRelease.await(5, TimeUnit.SECONDS)
                        return MockResponse().setResponseCode(sendResponseCode)
                    }
                    return MockResponse().setResponseCode(500)
                }
            }
    }

    private fun sendMessageThenLeaveBeforeServerReplies() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), ConnectMessagingActivity::class.java)
                .putExtra(ConnectMessagingActivity.CHANNEL_ID, channelId)
        val controller = Robolectric.buildActivity(ConnectMessagingActivity::class.java, intent).setup()
        ShadowLooper.idleMainLooper()

        val activity = controller.get()
        activity.findViewById<EditText>(R.id.etMessage).setText("Hello")
        activity.findViewById<View>(R.id.imgSendMessage).performClick()

        controller.pause().stop().destroy()
        sendResponseRelease.countDown()

        do {
            val request = mockApiServer.drainHttp()
        } while (!isSendRequest(request))
    }

    private fun isSendRequest(request: RecordedRequest): Boolean = request.path?.contains(PersonalIdApiEndpoints.MESSAGE_SEND_URL) == true
}
