package org.commcare.fragments.connect

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.tabs.TabLayout
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.activities.connect.ConnectMessagingActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectTaskRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.ConnectLearnJobTestData
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ConnectMockApiServer
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.ConnectSyncPreferences
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUnlocker
import org.commcare.views.connect.ConnectTaskCard
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Robolectric tests for [ConnectDeliveryMoreFragment], driven end to end: the job is seeded into the
 * real Connect database, tasks arrive over the real networking stack from [ConnectMockApiServer] in
 * the delivery-progress payload, and the tab is read through the pager it actually lives in.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectDeliveryMoreFragmentTest {
    private lateinit var activity: ConnectActivity
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus
    private lateinit var job: ConnectJobRecord
    private val mockApi = ConnectMockApiServer()

    @Volatile
    private var deliveryProgressBody: String = "{}"

    private val navController: NavController get() = navHostFragment.navController
    private val appContext get() = ApplicationProvider.getApplicationContext<CommCareTestApplication>()

    @Before
    fun setUp() {
        savedStatus = PersonalIdManager.getInstance().status
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.LoggedIn
        mockApi.start()
        mockApi.server.dispatcher = pathRoutingDispatcher()
        ConnectSyncPreferences.getInstance(appContext).clearAll()

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportConnectTabChange(any()) } returns Unit
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) = Unit
            }

        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false

        // Counts as unlocked this session, so opening a conversation task navigates rather than
        // raising a biometric prompt.
        PersonalIdUnlocker.lastUnlockTime = SystemClock.elapsedRealtime()

        seedConnectUser()
        job = seedDeliveryJob()

        activity =
            Robolectric
                .buildActivity(ConnectActivity::class.java)
                .create()
                .postCreate(null)
                .start()
                .resume()
                .get()
        navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connect) as NavHostFragment
    }

    @After
    fun tearDown() {
        PersonalIdManager.getInstance().status = savedStatus
        PersonalIdUnlocker.resetSession()
        mockApi.shutdown()
        unmockkAll()
    }

    @Test
    fun `pending tasks render as cards under the mandatory group`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = listOf(taskJson(name = "Nutrition Coaching Check-in"))))
        val mandatoryGroup = moreTab.requireView().findViewById<View>(R.id.delivery_tasks_mandatory_group)
        val emptyMessage = moreTab.requireView().findViewById<View>(R.id.delivery_tasks_empty)
        val cards = moreTab.taskCards()

        assertEquals(View.VISIBLE, mandatoryGroup.visibility)
        assertEquals(View.GONE, emptyMessage.visibility)
        assertEquals(1, cards.size)
        assertEquals("Nutrition Coaching Check-in", cards.first().renderedTitle())
    }

    @Test
    fun `tasks are ordered by expiry with only the soonest highlighted`() {
        val moreTab =
            openMoreTab(
                deliveryProgressJson(
                    tasks =
                        listOf(
                            taskJson(id = "later", name = "Later Task", dueDate = "2027-08-01"),
                            taskJson(id = "sooner", name = "Sooner Task", dueDate = "2027-01-15"),
                        ),
                ),
            )
        val cards = moreTab.taskCards()

        assertEquals(listOf("Sooner Task", "Later Task"), cards.map { it.renderedTitle() })
        assertTrue("soonest task is the primary action", cards[0].state.highlighted)
        assertFalse("later task is not highlighted", cards[1].state.highlighted)
    }

    @Test
    fun `a task card shows the date it expires on`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = listOf(taskJson(dueDate = "2027-07-24"))))
        val expected =
            activity.getString(
                R.string.connect_task_expires_on,
                dateFormat().format(dateOf(2027, Calendar.JULY, 24)),
            )

        assertEquals(expected, moreTab.taskCards().first().renderedExpiry())
    }

    @Test
    fun `cards sit the same distance apart as the design's gap`() {
        val moreTab =
            openMoreTab(
                deliveryProgressJson(
                    tasks =
                        listOf(
                            taskJson(id = "one", dueDate = "2027-01-01"),
                            taskJson(id = "two", dueDate = "2027-02-01"),
                        ),
                ),
            )
        val cards = moreTab.taskCards()

        assertEquals(
            activity.resources.getDimensionPixelSize(R.dimen.connect_space_md),
            (cards[1].layoutParams as LinearLayout.LayoutParams).topMargin,
        )
        assertEquals(
            "the first card starts flush with the group",
            0,
            (cards[0].layoutParams as LinearLayout.LayoutParams).topMargin,
        )
    }

    @Test
    fun `the optional group stays hidden while no task can be non-blocking`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = listOf(taskJson())))
        val optionalGroup = moreTab.requireView().findViewById<View>(R.id.delivery_tasks_optional_group)

        assertEquals(View.GONE, optionalGroup.visibility)
    }

    @Test
    fun `with no pending task the empty message replaces the groups`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        val emptyMessage = moreTab.requireView().findViewById<TextView>(R.id.delivery_tasks_empty)
        val mandatoryGroup = moreTab.requireView().findViewById<View>(R.id.delivery_tasks_mandatory_group)

        assertEquals(View.VISIBLE, emptyMessage.visibility)
        assertEquals(activity.getString(R.string.connect_delivery_tasks_empty), emptyMessage.text.toString())
        assertEquals(View.GONE, mandatoryGroup.visibility)
        assertEquals(0, moreTab.taskCards().size)
    }

    @Test
    fun `a completed task is not listed`() {
        val moreTab =
            openMoreTab(deliveryProgressJson(tasks = listOf(taskJson(status = ConnectTaskRecord.STATUS_COMPLETED))))
        val emptyMessage = moreTab.requireView().findViewById<View>(R.id.delivery_tasks_empty)

        assertEquals(0, moreTab.taskCards().size)
        assertEquals(View.VISIBLE, emptyMessage.visibility)
    }

    @Test
    fun `tapping a conversation task opens messaging on its channel`() {
        val moreTab =
            openMoreTab(
                deliveryProgressJson(
                    tasks = listOf(taskJson(mode = ConnectTaskRecord.MODE_OCS, channelId = "channel-7")),
                ),
            )

        activity.runOnUiThread { moreTab.taskCards().first().performClick() }
        ShadowLooper.idleMainLooper()

        val started = shadowOf(activity).nextStartedActivity
        assertNotNull("messaging should have been launched", started)
        assertEquals(ConnectMessagingActivity::class.java.name, started.component?.className)
        assertEquals("channel-7", started.getStringExtra(ConnectMessagingActivity.CHANNEL_ID))
    }

    @Test
    fun `tapping a relearn task goes to the download screen while the delivery app is missing`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = listOf(taskJson(mode = RELEARN_MODE))))

        activity.runOnUiThread { moreTab.taskCards().first().performClick() }
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.connect_downloading_fragment, navController.currentDestination?.id)
        assertEquals(
            activity.getString(R.string.connect_downloading_delivery),
            navController.currentBackStackEntry?.arguments?.getString(DOWNLOAD_TITLE_ARG),
        )
        assertFalse(
            "a relearn task downloads the delivery app, not the learn app",
            navController.currentBackStackEntry!!.arguments!!.getBoolean(DOWNLOAD_LEARNING_ARG, true),
        )
    }

    @Test
    fun `a conversation task and a relearn task take different icons`() {
        val moreTab =
            openMoreTab(
                deliveryProgressJson(
                    tasks =
                        listOf(
                            taskJson(id = "chat", mode = ConnectTaskRecord.MODE_OCS, dueDate = "2027-01-01"),
                            taskJson(id = "relearn", mode = RELEARN_MODE, dueDate = "2027-02-01"),
                        ),
                ),
            )
        val cards = moreTab.taskCards()

        assertEquals(R.drawable.ic_chat_bubble_outline, cards[0].state.iconRes)
        assertEquals(R.drawable.ic_connect_learn_app, cards[1].state.iconRes)
    }

    @Test
    fun `the revisit learning card names the opportunity and when learning was completed`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        val title = moreTab.requireView().findViewById<TextView>(R.id.revisit_learning_title)
        val completedLine = moreTab.requireView().findViewById<TextView>(R.id.revisit_learning_completed)

        assertEquals(ConnectLearnJobTestData.JOB_TITLE, title.text.toString())
        assertEquals(
            activity.getString(
                R.string.connect_delivery_revisit_learning_completed,
                dateFormat().format(dateOf(2026, Calendar.FEBRUARY, 21)),
            ),
            completedLine.text.toString(),
        )
    }

    @Test
    fun `the certificate stays collapsed until its button is tapped`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        val certificate = moreTab.certificate()
        val subject = moreTab.requireView().findViewById<TextView>(R.id.cert_subject_text)

        assertEquals(View.GONE, certificate.visibility)

        moreTab.performClick(R.id.revisit_learning_certificate_button)
        assertEquals(View.VISIBLE, certificate.visibility)
        assertEquals(ConnectLearnJobTestData.JOB_TITLE, subject.text.toString())

        moreTab.performClick(R.id.revisit_learning_certificate_button)
        assertEquals(View.GONE, certificate.visibility)
    }

    @Test
    fun `the certificate names the learner and their score`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        moreTab.performClick(R.id.revisit_learning_certificate_button)
        val learnerName = moreTab.requireView().findViewById<TextView>(R.id.cert_person_text)
        val score = moreTab.requireView().findViewById<TextView>(R.id.cert_score_text)

        assertEquals(LEARNER_NAME, learnerName.text.toString())
        assertEquals(
            activity.getString(
                R.string.connect_learn_cert_score,
                (ConnectLearnJobTestData.PASSING_SCORE + 10).toString(),
            ),
            score.text.toString(),
        )
    }

    @Test
    fun `an expanded certificate stays open across a data refresh`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        moreTab.performClick(R.id.revisit_learning_certificate_button)

        activity.runOnUiThread { moreTab.updateView() }
        ShadowLooper.idleMainLooper()

        assertEquals(View.VISIBLE, moreTab.certificate().visibility)
    }

    @Test
    fun `revisiting learning is offered even before the device has synced any learning`() {
        ConnectJobUtils.storeAssessments(appContext, emptyList(), ConnectLearnJobTestData.JOB_UUID, true)
        job = ConnectJobUtils.getCompositeJob(appContext, ConnectLearnJobTestData.JOB_UUID)!!

        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        val view = moreTab.requireView()
        val revisitGroup = view.findViewById<View>(R.id.revisit_learning_group)
        val viewButton = view.findViewById<View>(R.id.revisit_learning_view_button)
        val completedLine = view.findViewById<View>(R.id.revisit_learning_completed)
        val certificateButton = view.findViewById<View>(R.id.revisit_learning_certificate_button)

        assertEquals(View.VISIBLE, revisitGroup.visibility)
        assertEquals(View.VISIBLE, viewButton.visibility)
        assertEquals(
            "no completion date is invented for records the device does not hold",
            View.GONE,
            completedLine.visibility,
        )
        assertEquals(View.GONE, certificateButton.visibility)
    }

    @Test
    fun `viewing learning goes to the download screen while the learn app is missing`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))

        moreTab.performClick(R.id.revisit_learning_view_button)
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.connect_downloading_fragment, navController.currentDestination?.id)
        assertEquals(
            activity.getString(R.string.connect_downloading_learn),
            navController.currentBackStackEntry?.arguments?.getString(DOWNLOAD_TITLE_ARG),
        )
        assertTrue(
            navController.currentBackStackEntry!!.arguments!!.getBoolean(DOWNLOAD_LEARNING_ARG, false),
        )
    }

    @Test
    fun `the more tab is badged with the number of pending tasks`() {
        openMoreTab(
            deliveryProgressJson(
                tasks =
                    listOf(
                        taskJson(id = "one", dueDate = "2027-01-01"),
                        taskJson(id = "two", dueDate = "2027-02-01"),
                    ),
            ),
        )

        assertEquals(2, moreTabHeader().orCreateBadge.number)
    }

    @Test
    fun `the more tab carries no badge when nothing is pending`() {
        openMoreTab(deliveryProgressJson(tasks = emptyList()))

        assertNull(moreTabHeader().badge)
    }

    @Test
    fun `the badge clears once the last pending task is done`() {
        openMoreTab(deliveryProgressJson(tasks = listOf(taskJson())))
        assertEquals(1, moreTabHeader().orCreateBadge.number)

        resyncDeliveryProgress(deliveryProgressJson(tasks = emptyList()))

        assertNull("a completed task must not leave its badge behind", moreTabHeader().badge)
    }

    @Test
    fun `the shared launch bar hides on the more tab and returns with the dashboard`() {
        openMoreTab(deliveryProgressJson(tasks = emptyList()))
        val home = homeFragment()
        val ctaBar = home.requireView().findViewById<View>(R.id.connect_delivery_cta_bar)

        assertEquals(View.GONE, ctaBar.visibility)

        activity.runOnUiThread {
            home
                .requireView()
                .findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.connect_delivery_home_view_pager)
                .setCurrentItem(ConnectDeliveryHomeFragment.TAB_DASHBOARD, false)
        }
        ShadowLooper.idleMainLooper()

        assertEquals(View.VISIBLE, ctaBar.visibility)
    }

    /**
     * Navigates to the delivery home tabs on the More tab, answers the delivery-progress request the
     * screen fires on resume with [progressJson], and returns the More tab once it has rendered.
     */
    private fun openMoreTab(progressJson: String): ConnectDeliveryMoreFragment {
        deliveryProgressBody = progressJson

        activity.setActiveJob(job)
        activity.runOnUiThread {
            navController.navigate(
                R.id.action_connect_jobs_list_fragment_to_connect_job_delivery_progress_fragment,
                Bundle().apply {
                    putInt(ConnectDeliveryHomeFragment.TAB_POSITION, ConnectDeliveryHomeFragment.TAB_MORE)
                },
            )
        }
        ShadowLooper.idleMainLooper()

        awaitDeliverySync()
        layOutHierarchy()

        val moreTab =
            homeFragment()
                .childFragmentManager
                .fragments
                .filterIsInstance<ConnectDeliveryMoreFragment>()
                .first()

        // The first pass instantiates the page; this one measures and positions the view it created,
        // so tests can assert on real bounds.
        layOutHierarchy()
        return moreTab
    }

    /** Answers a second delivery-progress request with [progressJson] and waits for it to land. */
    private fun resyncDeliveryProgress(progressJson: String) {
        deliveryProgressBody = progressJson
        ConnectSyncPreferences.getInstance(appContext).clearAll()

        activity.runOnUiThread { homeFragment().refresh(true) }
        ShadowLooper.idleMainLooper()

        awaitDeliverySync()
        layOutHierarchy()
    }

    private fun homeFragment(): ConnectDeliveryHomeFragment =
        navHostFragment.childFragmentManager.primaryNavigationFragment as ConnectDeliveryHomeFragment

    /** The More entry in the tab strip, which carries the pending-task badge. */
    private fun moreTabHeader(): TabLayout.Tab =
        homeFragment()
            .requireView()
            .findViewById<TabLayout>(R.id.connect_delivery_home_tabs)
            .getTabAt(ConnectDeliveryHomeFragment.TAB_MORE)!!

    private fun ConnectDeliveryMoreFragment.taskCards(): List<ConnectTaskCard> {
        val list = requireView().findViewById<LinearLayout>(R.id.delivery_tasks_mandatory_list)
        return (0 until list.childCount).map { index -> list.getChildAt(index) as ConnectTaskCard }
    }

    private fun ConnectTaskCard.renderedTitle(): String = findViewById<TextView>(R.id.task_card_title).text.toString()

    private fun ConnectTaskCard.renderedExpiry(): String = findViewById<TextView>(R.id.task_card_expiry).text.toString()

    private fun ConnectDeliveryMoreFragment.certificate(): View = requireView().findViewById(R.id.revisit_learning_certificate)

    private fun ConnectDeliveryMoreFragment.performClick(viewId: Int) {
        requireActivity().runOnUiThread { requireView().findViewById<View>(viewId).performClick() }
        ShadowLooper.idleMainLooper()
    }

    /**
     * The More tab is a [androidx.viewpager2.widget.ViewPager2] page, so it is only instantiated once
     * the pager has been measured and laid out.
     */
    private fun layOutHierarchy() {
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT_PX, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX)
        ShadowLooper.idleMainLooper()
    }

    /**
     * Answers by path so the opportunity-list sync the start destination fires cannot consume the
     * tab's response. An empty opportunities body is deliberate: a parsed empty list would prune the
     * seeded job out of the database.
     */
    private fun pathRoutingDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body =
                    if (request.path?.endsWith(DELIVERY_PROGRESS_PATH) == true) {
                        deliveryProgressBody
                    } else {
                        ""
                    }
                return MockResponse().setResponseCode(200).setBody(body)
            }
        }

    /**
     * Waits for the delivery-progress response to be applied — the sync timestamp is written right
     * after the database write — then drains the main looper so the resulting UI update lands.
     */
    private fun awaitDeliverySync() {
        val syncKey = ConnectRepository.SYNC_KEY_DELIVERY_PREFIX + ConnectLearnJobTestData.JOB_UUID
        val prefs = ConnectSyncPreferences.getInstance(appContext)
        val deadline = System.currentTimeMillis() + SYNC_TIMEOUT_MS

        while (prefs.getLastSyncTime(syncKey) == null) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Delivery progress was never synced within ${SYNC_TIMEOUT_MS}ms")
            }
            ShadowLooper.idleMainLooper()
            Thread.sleep(POLL_INTERVAL_MS)
        }
        ShadowLooper.idleMainLooper()
    }

    private fun deliveryProgressJson(tasks: List<String>): String = """{"deliveries": [], "assigned_tasks": [${tasks.joinToString(",")}]}"""

    private fun taskJson(
        id: String = "task-1",
        name: String = "Nutrition Coaching Check-in",
        status: String = ConnectTaskRecord.STATUS_ASSIGNED,
        mode: String = RELEARN_MODE,
        dueDate: String = "2027-07-24",
        channelId: String = "",
    ): String =
        JSONObject()
            .apply {
                put("assigned_task_id", id)
                put("task_name", name)
                put("task_description", "Task description")
                put("status", status)
                put("task_mode", mode)
                put("due_date", dueDate)
                put("connect_channel_id", channelId)
            }.toString()

    private fun dateFormat(): DateFormat = DateFormat.getDateInstance(DateFormat.LONG)

    private fun dateOf(
        year: Int,
        month: Int,
        day: Int,
    ): Date = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }.time

    /**
     * Writes the opportunity through the real storage layer, with an assessment so the revisit card
     * has a learning completion date to show.
     */
    private fun seedDeliveryJob(): ConnectJobRecord {
        val seeded =
            ConnectLearnJobTestData.job().apply {
                status = ConnectJobRecord.STATUS_DELIVERING
            }
        ConnectJobUtils.storeJobs(appContext, listOf(seeded), true)
        ConnectJobUtils.storeAssessments(
            appContext,
            seeded.assessments,
            ConnectLearnJobTestData.JOB_UUID,
            true,
        )
        return ConnectJobUtils.getCompositeJob(appContext, ConnectLearnJobTestData.JOB_UUID)!!
    }

    /**
     * Writes a real user carrying a live Connect token so the progress call skips the SSO round-trip.
     * [ConnectDatabaseHelper.dbExists] has to be stubbed because it probes for the on-disk connect db,
     * which never exists under the in-memory test open helper.
     */
    private fun seedConnectUser() {
        mockkStatic(ConnectDatabaseHelper::class)
        every { ConnectDatabaseHelper.dbExists() } returns true

        val user =
            ConnectUserRecord(
                "1234567890",
                "test-user-id",
                "password",
                LEARNER_NAME,
                "1234",
                Date(),
                null,
                false,
                PersonalIdSessionData.PIN,
                true,
            )
        user.updateConnectToken("test-token", tomorrow())
        ConnectUserDatabaseUtil.storeUser(appContext, user)
    }

    private fun tomorrow(): Date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time

    private companion object {
        const val DELIVERY_PROGRESS_PATH = "/delivery_progress"
        const val DOWNLOAD_TITLE_ARG = "title"
        const val DOWNLOAD_LEARNING_ARG = "learning"
        const val LEARNER_NAME = "Test User"
        const val RELEARN_MODE = "relearn"
        const val SYNC_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 20L
        const val SCREEN_WIDTH_PX = 1080
        const val SCREEN_HEIGHT_PX = 1920
    }
}
