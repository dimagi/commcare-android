package org.commcare.activities

import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.activities.connect.viewmodel.PushNotificationViewModel
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.ConnectConstants
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.pn.helper.NotificationBroadcastHelper
import org.commcare.pn.workermanager.NotificationsSyncWorkerManager
import org.commcare.preferences.NotificationPrefs
import org.commcare.utils.PushNotificationApiHelper
import org.commcare.utils.coroutines.DispatcherProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PushNotificationActivityTest {
    private lateinit var activityController: ActivityController<PushNotificationActivity>
    private lateinit var activity: PushNotificationActivity

    private val dbReadCount = AtomicInteger(0)
    private val apiCallCount = AtomicInteger(0)
    private var dbNotifications: List<PushNotificationRecord> = emptyList()
    private var apiResult: suspend () -> Result<List<PushNotificationRecord>> = { Result.success(emptyList()) }
    private lateinit var savedPersonalIdStatus: PersonalIdManager.PersonalIdStatus

    private val recyclerView: RecyclerView
        get() = activity.findViewById(R.id.rvNotifications)
    private val emptyView: View
        get() = activity.findViewById(R.id.tvNoNotifications)

    @Before
    fun setUp() {
        // Run the ViewModel's coroutine on a deterministic test dispatcher instead of
        // the real Dispatchers.IO thread pool, so notification loading is synchronous.
        mockkObject(DispatcherProvider)
        every { DispatcherProvider.io() } returns UnconfinedTestDispatcher()

        // getIntentForPNClick is exercised for real, so cccCheckPassed() must see a logged-in
        // user. init() is a no-op unless status == NotIntroduced, so setting LoggedIn is enough.
        val personalIdManager = PersonalIdManager.getInstance()
        savedPersonalIdStatus = personalIdManager.status
        personalIdManager.status = PersonalIdManager.PersonalIdStatus.LoggedIn

        mockkObject(NotificationRecordDatabaseHelper)
        mockkObject(PushNotificationApiHelper)
        mockkConstructor(NotificationsSyncWorkerManager::class)
        mockkStatic(FirebaseAnalyticsUtil::class)

        every { NotificationRecordDatabaseHelper.getAllNotifications(any()) } answers {
            dbReadCount.incrementAndGet()
            dbNotifications
        }
        every { NotificationRecordDatabaseHelper.updateReadStatus(any(), any(), any()) } just Runs
        coEvery { PushNotificationApiHelper.retrieveLatestPushNotifications(any()) } coAnswers {
            apiCallCount.incrementAndGet()
            apiResult()
        }
        every { anyConstructed<NotificationsSyncWorkerManager>().startSyncWorkers(any()) } just Runs
        every { FirebaseAnalyticsUtil.reportNotificationEvent(any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        if (::activityController.isInitialized) {
            try {
                activityController.pause().stop().destroy()
            } catch (_: IllegalStateException) {
                // Ignore teardown errors for already-finished activities
            }
        }
        unmockkAll()
        PersonalIdManager.getInstance().status = savedPersonalIdStatus
    }

    private fun launchActivity() {
        activityController = Robolectric.buildActivity(PushNotificationActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .visible()
                .get()
    }

    private fun buildRecord(
        id: String,
        date: Date = Date(0L),
        action: String = ConnectConstants.CCC_DEST_PAYMENTS,
        opportunityUuid: String = "",
        paymentUuid: String = "",
    ): PushNotificationRecord =
        PushNotificationRecord().apply {
            notificationId = id
            this.action = action
            title = "Title $id"
            body = "Body $id"
            createdDate = date
            opportunityUUID = opportunityUuid
            paymentUUID = paymentUuid
        }

    private fun menuItemWithId(id: Int): MenuItem = mockk { every { itemId } returns id }

    @Test
    fun `notifications from local storage are shown in the list`() {
        dbNotifications = listOf(buildRecord("a"), buildRecord("b"))
        // Keep the api call pending so the list is populated from local storage alone
        val pendingApiCall = CompletableDeferred<Result<List<PushNotificationRecord>>>()
        apiResult = { pendingApiCall.await() }

        launchActivity()

        ShadowLooper.idleMainLooper()
        assertEquals(View.VISIBLE, recyclerView.visibility)
        assertEquals(View.GONE, emptyView.visibility)
        assertEquals(2, recyclerView.adapter!!.itemCount)

        pendingApiCall.complete(Result.success(emptyList()))

        ShadowLooper.idleMainLooper()
        assertEquals(View.VISIBLE, recyclerView.visibility)
        assertEquals(2, recyclerView.adapter!!.itemCount)
    }

    @Test
    fun `empty state is shown when there are no notifications`() {
        launchActivity()

        ShadowLooper.idleMainLooper()
        assertEquals(View.VISIBLE, emptyView.visibility)
        assertEquals(View.GONE, recyclerView.visibility)
    }

    @Test
    fun `list and empty state stay hidden while notifications are loading`() {
        val pendingApiCall = CompletableDeferred<Result<List<PushNotificationRecord>>>()
        apiResult = { pendingApiCall.await() }

        launchActivity()

        ShadowLooper.idleMainLooper()
        assertEquals(View.GONE, recyclerView.visibility)
        assertEquals(View.GONE, emptyView.visibility)

        pendingApiCall.complete(Result.success(listOf(buildRecord("a"))))

        ShadowLooper.idleMainLooper()
        assertEquals(View.VISIBLE, recyclerView.visibility)
        assertEquals(View.GONE, emptyView.visibility)
        assertEquals(1, recyclerView.adapter!!.itemCount)
    }

    @Test
    fun `new api notifications are merged with cached ones and sorted newest first`() {
        // The API returns only new notifications; the cached one comes from local storage.
        dbNotifications = listOf(buildRecord("cached", Date(1000L)))
        val pendingApiCall = CompletableDeferred<Result<List<PushNotificationRecord>>>()
        apiResult = { pendingApiCall.await() }

        launchActivity()

        // Let the cached list from local storage publish before the api responds.
        ShadowLooper.idleMainLooper()
        pendingApiCall.complete(Result.success(listOf(buildRecord("api", Date(2000L)))))
        ShadowLooper.idleMainLooper()

        // Assert the merge on the ViewModel's output: a second non-empty submitList would diff
        // on a background thread that idleMainLooper does not flush, so the adapter is unreliable.
        val viewModel = ViewModelProvider(activity)[PushNotificationViewModel::class.java]
        val merged = viewModel.allNotifications.value!!
        assertEquals(listOf("api", "cached"), merged.map { it.notificationId })

        verify { anyConstructed<NotificationsSyncWorkerManager>().startSyncWorkers(any()) }
    }

    @Test
    fun `notifications are marked as read when the screen shows them`() {
        NotificationPrefs.setNotificationAsUnread(ApplicationProvider.getApplicationContext<CommCareTestApplication>())

        launchActivity()

        ShadowLooper.idleMainLooper()
        assertTrue(NotificationPrefs.getNotificationReadStatus(activity))
    }

    @Test
    fun `a toast is shown when fetching notifications fails`() {
        apiResult = { Result.failure(Exception("Network error")) }

        launchActivity()

        ShadowLooper.idleMainLooper()
        assertEquals("Network error", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `cloud sync menu item refreshes from the server without re-reading local storage`() {
        launchActivity()
        ShadowLooper.idleMainLooper()
        assertEquals(1, apiCallCount.get())

        assertTrue(activity.onOptionsItemSelected(menuItemWithId(R.id.notification_cloud_sync)))

        ShadowLooper.idleMainLooper()
        assertEquals(2, apiCallCount.get())
        assertEquals(1, dbReadCount.get())
    }

    @Test
    fun `home menu item closes the screen`() {
        launchActivity()

        assertTrue(activity.onOptionsItemSelected(menuItemWithId(android.R.id.home)))

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `a new notification broadcast reloads notifications from local storage`() {
        launchActivity()
        ShadowLooper.idleMainLooper()
        assertEquals(1, dbReadCount.get())
        assertEquals(1, apiCallCount.get())

        NotificationBroadcastHelper.sendNewNotificationBroadcast(activity)

        ShadowLooper.idleMainLooper()
        assertEquals(2, dbReadCount.get())
    }

    private fun launchAndClickFirstNotification(record: PushNotificationRecord) {
        apiResult = { Result.success(listOf(record)) }
        launchActivity()
        ShadowLooper.idleMainLooper()
        assertEquals(View.VISIBLE, recyclerView.visibility)
        recyclerView.findViewHolderForAdapterPosition(0)!!.itemView.performClick()
    }

    @Test
    fun `clicking a payment notification reports analytics, marks it read, and opens ConnectActivity`() {
        val record = buildRecord("a", action = ConnectConstants.CCC_DEST_PAYMENTS, paymentUuid = "pay-1")

        launchAndClickFirstNotification(record)

        verify {
            FirebaseAnalyticsUtil.reportNotificationEvent(
                AnalyticsParamValue.NOTIFICATION_EVENT_TYPE_CLICK,
                AnalyticsParamValue.REPORT_NOTIFICATION_CLICK_NOTIFICATION_HISTORY,
                record.getNotificationActionFromRecord(),
                record.notificationId,
            )
        }
        verify { NotificationRecordDatabaseHelper.updateReadStatus(any(), "a", true) }

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ConnectActivity::class.java.name, started.component!!.className)
        assertEquals(ConnectConstants.CCC_DEST_PAYMENTS, started.getStringExtra(ConnectConstants.REDIRECT_ACTION))
        assertEquals("pay-1", started.getStringExtra(ConnectConstants.PAYMENT_UUID))
    }

    @Test
    fun `clicking a learn-progress notification opens ConnectActivity for the opportunity`() {
        val record =
            buildRecord("a", action = ConnectConstants.CCC_DEST_LEARN_PROGRESS, opportunityUuid = "opp-learn")

        launchAndClickFirstNotification(record)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ConnectActivity::class.java.name, started.component!!.className)
        assertEquals(ConnectConstants.CCC_DEST_LEARN_PROGRESS, started.getStringExtra(ConnectConstants.REDIRECT_ACTION))
        assertEquals("opp-learn", started.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
    }

    @Test
    fun `clicking a delivery-progress notification opens ConnectActivity for the opportunity`() {
        val record =
            buildRecord("a", action = ConnectConstants.CCC_DEST_DELIVERY_PROGRESS, opportunityUuid = "opp-deliver")

        launchAndClickFirstNotification(record)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ConnectActivity::class.java.name, started.component!!.className)
        assertEquals(
            ConnectConstants.CCC_DEST_DELIVERY_PROGRESS,
            started.getStringExtra(ConnectConstants.REDIRECT_ACTION),
        )
        assertEquals("opp-deliver", started.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
    }

    @Test
    fun `clicking an opportunity-summary notification opens ConnectActivity for the opportunity`() {
        val record =
            buildRecord(
                "a",
                action = ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
                opportunityUuid = "opp-summary",
            )

        launchAndClickFirstNotification(record)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ConnectActivity::class.java.name, started.component!!.className)
        assertEquals(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            started.getStringExtra(ConnectConstants.REDIRECT_ACTION),
        )
        assertEquals("opp-summary", started.getStringExtra(ConnectConstants.OPPORTUNITY_UUID))
    }

    @Test
    fun `clicking a message notification opens ConnectMessagingActivity`() {
        val record = buildRecord("a", action = ConnectConstants.CCC_MESSAGE)

        launchAndClickFirstNotification(record)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ConnectMessagingActivity::class.java.name, started.component!!.className)
        assertEquals(ConnectConstants.CCC_MESSAGE, started.getStringExtra(ConnectConstants.REDIRECT_ACTION))
    }

    @Test
    fun `clicking a notification does not navigate when the user is not logged in`() {
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.Registering
        val record = buildRecord("a", action = ConnectConstants.CCC_DEST_PAYMENTS)

        launchAndClickFirstNotification(record)

        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
