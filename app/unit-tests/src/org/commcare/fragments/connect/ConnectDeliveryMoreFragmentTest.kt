package org.commcare.fragments.connect

import android.net.ConnectivityManager
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
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
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
import org.commcare.connect.ConnectAppUtils
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
import org.commcare.views.dialogs.CustomProgressDialog
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
        ConnectSyncPreferences.getInstance().clearAll()

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

        // A missing app now installs in place, so the download is stubbed out rather than run.
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.downloadApp(any(), any()) } returns true

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
    fun `with no pending task the empty message replaces the group`() {
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
    fun `tapping a relearn task downloads the delivery app without leaving the tab`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = listOf(taskJson(mode = RELEARN_MODE))))

        activity.runOnUiThread { moreTab.taskCards().first().performClick() }
        ShadowLooper.idleMainLooper()

        verify {
            ConnectAppUtils.downloadApp(ConnectLearnJobTestData.DELIVERY_APP_INSTALL_URL, any())
        }
        assertEquals(R.id.connect_delivery_home_fragment, navController.currentDestination?.id)
        assertInstallDialogShowing(moreTab, R.string.connect_downloading_delivery)
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
        job = ConnectJobUtils.getCompositeJob(ConnectLearnJobTestData.JOB_UUID)!!

        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        val view = moreTab.requireView()
        val revisitGroup = view.findViewById<View>(R.id.revisit_learning_group)
        val viewButton = view.findViewById<View>(R.id.revisit_learning_view_button)
        val completedLine = view.findViewById<View>(R.id.revisit_learning_completed)
        val certificateButton = view.findViewById<View>(R.id.revisit_learning_certificate_button)

        assertEquals(View.VISIBLE, revisitGroup.visibility)
        assertEquals(View.VISIBLE, viewButton.visibility)
        assertEquals(
            "the certificate reads as pending rather than absent",
            View.VISIBLE,
            certificateButton.visibility,
        )
        assertFalse("it cannot be opened until the records arrive", certificateButton.isEnabled)
        assertEquals(
            "no completion date is invented for records the device does not hold",
            activity.getString(R.string.connect_delivery_revisit_learning_unsynced),
            (completedLine as TextView).text.toString(),
        )
        assertEquals(View.GONE, moreTab.certificate().visibility)
    }

    @Test
    fun `offline the card says the certificate waits on a connection rather than a sync`() {
        ConnectJobUtils.storeAssessments(appContext, emptyList(), ConnectLearnJobTestData.JOB_UUID, true)
        job = ConnectJobUtils.getCompositeJob(ConnectLearnJobTestData.JOB_UUID)!!

        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        goOffline()
        activity.runOnUiThread { moreTab.updateView() }

        val completedLine = moreTab.requireView().findViewById<TextView>(R.id.revisit_learning_completed)
        assertEquals(
            activity.getString(R.string.connect_delivery_revisit_learning_offline),
            completedLine.text.toString(),
        )
    }

    @Test
    fun `a synced certificate leaves the button enabled and the waiting message gone`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))
        val certificateButton = moreTab.requireView().findViewById<View>(R.id.revisit_learning_certificate_button)
        val completedLine = moreTab.requireView().findViewById<TextView>(R.id.revisit_learning_completed)

        assertTrue(certificateButton.isEnabled)
        assertFalse(
            completedLine.text.toString().contains(
                activity.getString(R.string.connect_delivery_revisit_learning_unsynced),
            ),
        )
    }

    /**
     * Only one app installs at a time, so a tab opened while another screen's install is running has
     * to report that install rather than start a second one it would then wait on forever.
     */
    @Test
    fun `a task tapped while an install is already running does not start a second download`() {
        openMoreTab(deliveryProgressJson(tasks = listOf(taskJson(mode = RELEARN_MODE))))
        startInstallFromDashboard()
        selectTab(ConnectDeliveryHomeFragment.TAB_MORE)
        val moreTab = moreTabFragment()

        activity.runOnUiThread { moreTab.taskCards().first().performClick() }
        ShadowLooper.idleMainLooper()

        verify(exactly = 1) { ConnectAppUtils.downloadApp(any(), any()) }
        assertNull(
            "the More tab must not raise a dialog over an install it does not own",
            moreTab.childFragmentManager.findFragmentByTag(INSTALL_DIALOG_TAG),
        )
    }

    /** The launch bar is the only sign of an install, so the More tab stops hiding it during one. */
    @Test
    fun `the launch bar stays visible on the More tab while an install runs`() {
        openMoreTab(deliveryProgressJson(tasks = emptyList()))
        assertEquals(View.GONE, deliveryCtaBar().visibility)

        startInstallFromDashboard()
        selectTab(ConnectDeliveryHomeFragment.TAB_MORE)

        assertEquals(View.VISIBLE, deliveryCtaBar().visibility)
    }

    @Test
    fun `viewing learning downloads the learn app without leaving the tab`() {
        val moreTab = openMoreTab(deliveryProgressJson(tasks = emptyList()))

        moreTab.performClick(R.id.revisit_learning_view_button)
        ShadowLooper.idleMainLooper()

        verify { ConnectAppUtils.downloadApp(ConnectLearnJobTestData.LEARN_APP_INSTALL_URL, any()) }
        assertEquals(R.id.connect_delivery_home_fragment, navController.currentDestination?.id)
        assertInstallDialogShowing(moreTab, R.string.connect_downloading_learn)
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

        assertEquals(View.VISIBLE, moreTabBadge().visibility)
        assertEquals("2", moreTabBadge().text.toString())
    }

    @Test
    fun `the more tab carries no badge when nothing is pending`() {
        openMoreTab(deliveryProgressJson(tasks = emptyList()))

        assertEquals(View.GONE, moreTabBadge().visibility)
    }

    @Test
    fun `the badge clears once the last pending task is done`() {
        openMoreTab(deliveryProgressJson(tasks = listOf(taskJson())))
        assertEquals(View.VISIBLE, moreTabBadge().visibility)

        resyncDeliveryProgress(deliveryProgressJson(tasks = emptyList()))

        assertEquals(
            "a completed task must not leave its badge behind",
            View.GONE,
            moreTabBadge().visibility,
        )
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
        ConnectSyncPreferences.getInstance().clearAll()

        activity.runOnUiThread { homeFragment().refresh(true) }
        ShadowLooper.idleMainLooper()

        awaitDeliverySync()
        layOutHierarchy()
    }

    private fun homeFragment(): ConnectDeliveryHomeFragment =
        navHostFragment.childFragmentManager.primaryNavigationFragment as ConnectDeliveryHomeFragment

    private fun moreTabFragment(): ConnectDeliveryMoreFragment =
        homeFragment()
            .childFragmentManager
            .fragments
            .filterIsInstance<ConnectDeliveryMoreFragment>()
            .first()

    private fun deliveryCtaBar(): View = homeFragment().requireView().findViewById(R.id.connect_delivery_cta_bar)

    private fun selectTab(position: Int) {
        val tabs =
            homeFragment().requireView().findViewById<TabLayout>(R.id.connect_delivery_home_tabs)
        activity.runOnUiThread { tabs.getTabAt(position)?.select() }
        ShadowLooper.idleMainLooper()
        layOutHierarchy()
    }

    /** Starts a delivery-app install the way a user does, from the launch bar on the Dashboard tab. */
    private fun startInstallFromDashboard() {
        selectTab(ConnectDeliveryHomeFragment.TAB_DASHBOARD)
        val ctaButton = deliveryCtaBar().findViewById<View>(R.id.cta_button)
        activity.runOnUiThread { ctaButton.performClick() }
        ShadowLooper.idleMainLooper()
    }

    /** The More entry in the tab strip, which carries the pending-task badge. */
    private fun moreTabHeader(): TabLayout.Tab =
        homeFragment()
            .requireView()
            .findViewById<TabLayout>(R.id.connect_delivery_home_tabs)
            .getTabAt(ConnectDeliveryHomeFragment.TAB_MORE)!!

    private fun moreTabBadge(): TextView = moreTabHeader().customView!!.findViewById(R.id.tab_badge)

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
        val prefs = ConnectSyncPreferences.getInstance()
        val deadline = System.currentTimeMillis() + SYNC_TIMEOUT_MS

        while (prefs.getLastSyncTime(syncKey) == null) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Delivery progress was never synced within ${SYNC_TIMEOUT_MS}ms")
            }
            ShadowLooper.idleMainLooper()
            Thread.sleep(POLL_INTERVAL_MS)
        }

        // The sync time is stored before the success is emitted, so the observers that redraw the
        // screen have not necessarily run yet when the wait above ends.
        repeat(POST_SYNC_IDLE_PASSES) {
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
        return ConnectJobUtils.getCompositeJob(ConnectLearnJobTestData.JOB_UUID)!!
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
        ConnectUserDatabaseUtil.storeUser(user)
    }

    private fun tomorrow(): Date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time

    /** Robolectric reports a connected network by default, so the offline copy needs one taken away. */
    private fun goOffline() {
        val manager = appContext.getSystemService(ConnectivityManager::class.java)
        shadowOf(manager).setActiveNetworkInfo(null)
    }

    /**
     * The More tab has no action bar of its own, so an install it starts is reported in a blocking
     * dialog naming [downloadingRes].
     */
    private fun assertInstallDialogShowing(
        moreTab: ConnectDeliveryMoreFragment,
        downloadingRes: Int,
    ) {
        val dialog =
            moreTab.childFragmentManager.findFragmentByTag(INSTALL_DIALOG_TAG) as? CustomProgressDialog
        assertNotNull("an install dialog should be showing", dialog)
        val message = dialog!!.requireDialog().findViewById<TextView>(R.id.progress_dialog_message)
        assertEquals(activity.getString(downloadingRes), message.text.toString())
    }

    private companion object {
        const val DELIVERY_PROGRESS_PATH = "/delivery_progress"
        const val INSTALL_DIALOG_TAG = "connect_install_progress"
        const val LEARNER_NAME = "Test User"
        const val RELEARN_MODE = "relearn"
        const val SYNC_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 20L
        const val POST_SYNC_IDLE_PASSES = 5
        const val SCREEN_WIDTH_PX = 1080
        const val SCREEN_HEIGHT_PX = 1920
    }
}
