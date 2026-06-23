package org.commcare.activities

import android.content.Intent
import android.view.MenuItem
import android.view.View
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
import org.commcare.CommCareTestApplication
import org.commcare.adapters.PushNotificationAdapter
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.database.NotificationRecordDatabaseHelper
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.pn.helper.NotificationBroadcastHelper
import org.commcare.pn.workermanager.NotificationsSyncWorkerManager
import org.commcare.preferences.NotificationPrefs
import org.commcare.utils.FirebaseMessagingUtil
import org.commcare.utils.PushNotificationApiHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PushNotificationActivityTest {
    private lateinit var activityController: ActivityController<PushNotificationActivity>
    private lateinit var activity: PushNotificationActivity

    private val dbReadCount = AtomicInteger(0)
    private val apiCallCount = AtomicInteger(0)
    private var dbNotifications: List<PushNotificationRecord> = emptyList()
    private var apiResult: suspend () -> Result<List<PushNotificationRecord>> = { Result.success(emptyList()) }

    private val recyclerView: RecyclerView
        get() = activity.findViewById(R.id.rvNotifications)
    private val emptyView: View
        get() = activity.findViewById(R.id.tvNoNotifications)

    @Before
    fun setUp() {
        mockkObject(NotificationRecordDatabaseHelper)
        mockkObject(PushNotificationApiHelper)
        mockkConstructor(NotificationsSyncWorkerManager::class)
        mockkStatic(FirebaseAnalyticsUtil::class)
        mockkStatic(FirebaseMessagingUtil::class)

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

    private fun waitFor(
        description: String,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timed out waiting for $description")
            }
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
    }

    private fun layoutRecyclerView() {
        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        recyclerView.layout(0, 0, 1080, 1920)
    }

    private fun buildRecord(
        id: String,
        date: Date = Date(0L),
    ): PushNotificationRecord =
        PushNotificationRecord().apply {
            notificationId = id
            action = "ccc_payment_page"
            title = "Title $id"
            body = "Body $id"
            createdDate = date
        }

    private fun menuItemWithId(id: Int): MenuItem = mockk { every { itemId } returns id }

    @Test
    fun `notifications from local storage are shown in the list`() {
        dbNotifications = listOf(buildRecord("a"), buildRecord("b"))
        // Keep the api call pending so the list is populated from local storage alone
        val pendingApiCall = CompletableDeferred<Result<List<PushNotificationRecord>>>()
        apiResult = { pendingApiCall.await() }

        launchActivity()

        waitFor("notification list to become visible") { recyclerView.visibility == View.VISIBLE }
        assertEquals(View.GONE, emptyView.visibility)
        assertEquals(2, recyclerView.adapter!!.itemCount)

        pendingApiCall.complete(Result.success(emptyList()))

        waitFor("list to stay populated after api returns nothing new") {
            ShadowLooper.idleMainLooper()
            recyclerView.visibility == View.VISIBLE && recyclerView.adapter!!.itemCount == 2
        }
    }

    @Test
    fun `empty state is shown when there are no notifications`() {
        launchActivity()

        waitFor("empty state to become visible") { emptyView.visibility == View.VISIBLE }
        assertEquals(View.GONE, recyclerView.visibility)
    }

    @Test
    fun `list and empty state stay hidden while notifications are loading`() {
        val pendingApiCall = CompletableDeferred<Result<List<PushNotificationRecord>>>()
        apiResult = { pendingApiCall.await() }

        launchActivity()

        waitFor("list to be hidden during load") { recyclerView.visibility == View.GONE }
        assertEquals(View.GONE, emptyView.visibility)

        pendingApiCall.complete(Result.success(listOf(buildRecord("a"))))

        waitFor("list to become visible") { recyclerView.visibility == View.VISIBLE }
        assertEquals(View.GONE, emptyView.visibility)
        assertEquals(1, recyclerView.adapter!!.itemCount)
    }

    @Test
    fun `api notifications are merged with cached ones and sorted newest first`() {
        dbNotifications = listOf(buildRecord("cached", Date(1000L)))
        apiResult = {
            Result.success(
                listOf(
                    buildRecord("api", Date(2000L)),
                    buildRecord("cached", Date(1000L)),
                ),
            )
        }

        launchActivity()

        waitFor("merged list to be shown") { recyclerView.adapter?.itemCount == 2 }
        val adapter = recyclerView.adapter as PushNotificationAdapter
        assertEquals("api", adapter.currentList[0].notificationId)
        assertEquals("cached", adapter.currentList[1].notificationId)

        verify { anyConstructed<NotificationsSyncWorkerManager>().startSyncWorkers(any()) }
    }

    @Test
    fun `notifications are marked as read when the screen shows them`() {
        NotificationPrefs.setNotificationAsUnread(ApplicationProvider.getApplicationContext<CommCareTestApplication>())

        launchActivity()

        waitFor("notification read status to update") { NotificationPrefs.getNotificationReadStatus(activity) }
        assertTrue(NotificationPrefs.getNotificationReadStatus(activity))
    }

    @Test
    fun `a toast is shown when fetching notifications fails`() {
        apiResult = { Result.failure(Exception("Network error")) }

        launchActivity()

        waitFor("error toast to be shown") { ShadowToast.getTextOfLatestToast() == "Network error" }
    }

    @Test
    fun `cloud sync menu item refreshes from the server without re-reading local storage`() {
        launchActivity()
        waitFor("initial load to complete") { apiCallCount.get() == 1 }

        assertTrue(activity.onOptionsItemSelected(menuItemWithId(R.id.notification_cloud_sync)))

        waitFor("refresh api call") { apiCallCount.get() == 2 }
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
        waitFor("initial load to complete") { dbReadCount.get() == 1 && apiCallCount.get() == 1 }

        NotificationBroadcastHelper.sendNewNotificationBroadcast(activity)

        waitFor("reload triggered by broadcast") { dbReadCount.get() == 2 }
    }

    @Test
    fun `clicking a notification reports analytics and opens the target screen`() {
        val record = buildRecord("a")
        apiResult = { Result.success(listOf(record)) }

        launchActivity()
        waitFor("notification list to become visible") { recyclerView.visibility == View.VISIBLE }
        layoutRecyclerView()

        val targetIntent = Intent(activity, DispatchActivity::class.java)
        every { FirebaseMessagingUtil.getIntentForPNClick(any(), any()) } returns targetIntent

        recyclerView.findViewHolderForAdapterPosition(0)!!.itemView.performClick()

        verify {
            FirebaseAnalyticsUtil.reportNotificationEvent(
                AnalyticsParamValue.NOTIFICATION_EVENT_TYPE_CLICK,
                AnalyticsParamValue.REPORT_NOTIFICATION_CLICK_NOTIFICATION_HISTORY,
                record.action,
                record.notificationId,
            )
        }
        verify { NotificationRecordDatabaseHelper.updateReadStatus(any(), "a", true) }
        assertEquals(targetIntent.component, shadowOf(activity).nextStartedActivity.component)
    }

    @Test
    fun `clicking a notification without a target intent does not open a screen`() {
        apiResult = { Result.success(listOf(buildRecord("a"))) }

        launchActivity()
        waitFor("notification list to become visible") { recyclerView.visibility == View.VISIBLE }
        layoutRecyclerView()

        every { FirebaseMessagingUtil.getIntentForPNClick(any(), any()) } returns null

        recyclerView.findViewHolderForAdapterPosition(0)!!.itemView.performClick()

        assertNull(shadowOf(activity).nextStartedActivity)
    }
}
