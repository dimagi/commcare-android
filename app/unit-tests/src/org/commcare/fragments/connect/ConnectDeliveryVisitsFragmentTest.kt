package org.commcare.fragments.connect

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.util.ReflectionUtils
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Robolectric tests for [ConnectDeliveryVisitsFragment], driven end to end: the job is seeded into
 * the real Connect database, deliveries arrive over the real networking stack from
 * [ConnectMockApiServer], and the payment unit cards are read through the tab they live in — down to
 * tapping one and asserting the visits it opens.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectDeliveryVisitsFragmentTest {
    private lateinit var activity: ConnectActivity
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus
    private lateinit var job: ConnectJobRecord
    private val mockApi = ConnectMockApiServer()

    @Volatile
    private var deliveryProgressBody: String = "{}"

    private val visitDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    private val endDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val navController: NavController get() = navHostFragment.navController
    private val appContext get() = ApplicationProvider.getApplicationContext<CommCareTestApplication>()

    /** Each unit's own cap, so a unit can be exhausted without ending the job. */
    private val perUnitTotal = ConnectLearnJobTestData.MAX_VISITS / ConnectLearnJobTestData.PAYMENT_UNIT_COUNT

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
        mockApi.shutdown()
        unmockkAll()
    }

    @Test
    fun `a card is shown for each payment unit`() {
        val cards =
            getPaymentUnitCards(
                launchVisitsTab(deliveryProgressJson(deliveries = approvedDeliveriesFor(unit = 1, count = 2))),
            )

        assertEquals(ConnectLearnJobTestData.PAYMENT_UNIT_COUNT, cards.size)
        assertEquals("Unit 1", cards[0].title())
        assertEquals("Unit 2", cards[1].title())
    }

    @Test
    fun `a card shows its unit's approved count, amount earned and visits remaining`() {
        val card =
            getPaymentUnitCards(
                launchVisitsTab(deliveryProgressJson(deliveries = approvedDeliveriesFor(unit = 1, count = 2))),
            )[0]

        assertEquals("2", card.approved())
        assertEquals("50 ${ConnectLearnJobTestData.CURRENCY}", card.amount())
        assertEquals(
            appContext.getString(
                R.string.connect_results_summary_remaining_days,
                perUnitTotal - 2,
                job.daysRemaining,
            ),
            card.remaining(),
        )
    }

    @Test
    fun `each card counts only its own unit's deliveries`() {
        val cards =
            getPaymentUnitCards(
                launchVisitsTab(
                    deliveryProgressJson(
                        deliveries =
                            approvedDeliveriesFor(unit = 1, count = 2) +
                                approvedDeliveriesFor(unit = 2, count = 3, startId = 50),
                    ),
                ),
            )

        assertEquals("2", cards[0].approved())
        assertEquals("3", cards[1].approved())
    }

    @Test
    fun `only approved deliveries count toward a unit's progress`() {
        val card =
            getPaymentUnitCards(
                launchVisitsTab(
                    deliveryProgressJson(
                        deliveries =
                            approvedDeliveriesFor(unit = 1, count = 2) +
                                deliveriesJsonFor(unit = 1, count = 4, status = "rejected", startId = 20) +
                                deliveriesJsonFor(unit = 1, count = 3, status = "pending", startId = 30),
                    ),
                ),
            )[0]

        assertEquals("2", card.approved())
        assertEquals("50 ${ConnectLearnJobTestData.CURRENCY}", card.amount())
    }

    @Test
    fun `the progress bar tracks the unit's share of its target`() {
        val cards =
            getPaymentUnitCards(
                launchVisitsTab(
                    deliveryProgressJson(
                        deliveries = approvedDeliveriesFor(unit = 1, count = perUnitTotal / 4),
                    ),
                ),
            )

        assertEquals(25f, cards[0].progress(), 0.01f)
        assertEquals("a unit with no deliveries sits at zero", 0f, cards[1].progress(), 0.01f)
    }

    @Test
    fun `a unit that has met its target reports that all visits are done`() {
        val cards =
            getPaymentUnitCards(
                launchVisitsTab(
                    deliveryProgressJson(deliveries = approvedDeliveriesFor(unit = 1, count = perUnitTotal)),
                ),
            )

        assertEquals(
            appContext.getString(R.string.connect_results_summary_visits_done),
            cards[0].remaining(),
        )
        assertEquals(100f, cards[0].progress(), 0.01f)
    }

    @Test
    fun `remaining visits are counted against the last day when the job ends today`() {
        val cards =
            getPaymentUnitCards(
                launchVisitsTab(
                    deliveryProgressJson(
                        deliveries = approvedDeliveriesFor(unit = 1, count = 2),
                        endDate = endDateDaysFromToday(0),
                    ),
                ),
            )

        assertEquals(
            appContext.getString(R.string.connect_results_summary_remaining_today, perUnitTotal - 2),
            cards[0].remaining(),
        )
    }

    @Test
    fun `a card reports the days are over once the job has ended`() {
        val cards =
            getPaymentUnitCards(
                launchVisitsTab(
                    deliveryProgressJson(
                        deliveries = approvedDeliveriesFor(unit = 1, count = 2),
                        endDate = endDateDaysFromToday(-1),
                    ),
                ),
            )

        assertEquals(
            appContext.getString(R.string.connect_results_summary_days_over),
            cards[0].remaining(),
        )
    }

    @Test
    fun `tapping a card opens that unit's visits and lists only its own deliveries`() {
        val visits =
            launchVisitsTab(
                deliveryProgressJson(
                    deliveries =
                        approvedDeliveriesFor(unit = 1, count = 2) +
                            approvedDeliveriesFor(unit = 2, count = 3, startId = 50),
                ),
            )
        val unitTwoCard = getPaymentUnitCards(visits)[1]

        activity.runOnUiThread { unitTwoCard.performClick() }
        ShadowLooper.idleMainLooper()
        layOutHierarchy()

        assertEquals(
            R.id.connect_delivery_visits_detail_fragment,
            navController.currentDestination?.id,
        )
        assertEquals(
            appContext.getString(R.string.connect_visit_type_title, "Unit 2"),
            activity.supportActionBar?.title,
        )
        assertEquals(
            "only the tapped unit's visits are listed",
            listOf("Entity 50", "Entity 51", "Entity 52"),
            visitNamesOnDetailScreen(),
        )
    }

    /**
     * Navigates to the delivery home tabs, answers the delivery-progress request the screen fires on
     * resume with [responseBody], then selects the Visits tab and returns it once laid out.
     */
    private fun launchVisitsTab(responseBody: String): ConnectDeliveryVisitsFragment {
        deliveryProgressBody = responseBody

        activity.setActiveJob(job)
        activity.runOnUiThread {
            navController.navigate(
                R.id.action_connect_jobs_list_fragment_to_connect_job_delivery_progress_fragment,
            )
        }
        ShadowLooper.idleMainLooper()

        awaitDeliverySync()
        layOutHierarchy()

        val home =
            navHostFragment.childFragmentManager.primaryNavigationFragment
                as ConnectDeliveryHomeFragment
        home
            .requireView()
            .findViewById<ViewPager2>(R.id.connect_delivery_home_view_pager)
            .setCurrentItem(ConnectDeliveryHomeFragment.TAB_VISITS, false)
        ShadowLooper.idleMainLooper()
        layOutHierarchy()

        val visits =
            home.childFragmentManager.fragments
                .filterIsInstance<ConnectDeliveryVisitsFragment>()
                .first()

        // The pass above instantiated the page; this one measures and positions the rows it created,
        // so the item views exist to assert on.
        layOutHierarchy()
        return visits
    }

    /**
     * The tab is a [androidx.viewpager2.widget.ViewPager2] page holding a
     * [RecyclerView], so neither the page nor its rows exist until the tree is measured.
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

    private fun getPaymentUnitCards(visits: ConnectDeliveryVisitsFragment): List<View> {
        val recycler = visits.requireView().findViewById<RecyclerView>(R.id.rvPaymentUnits)
        return (0 until recycler.childCount).map { recycler.getChildAt(it) }
    }

    private fun visitNamesOnDetailScreen(): List<String> {
        val detail =
            navHostFragment.childFragmentManager.primaryNavigationFragment
                as ConnectDeliveryVisitsDetailFragment
        val recycler = detail.requireView().findViewById<RecyclerView>(R.id.delivery_list)
        return (0 until recycler.childCount).map { index ->
            recycler
                .getChildAt(index)
                .findViewById<TextView>(R.id.delivery_item_name)
                .text
                .toString()
        }
    }

    private fun View.title(): String = findViewById<TextView>(R.id.tvDeliveryTitle).text.toString()

    private fun View.approved(): String = findViewById<TextView>(R.id.tvApproved).text.toString()

    private fun View.amount(): String = findViewById<TextView>(R.id.tvDeliveryTotalAmount).text.toString()

    private fun View.remaining(): String = findViewById<TextView>(R.id.tvRemaining).text.toString()

    /** [org.commcare.views.connect.LinearProgressBar] draws its progress without exposing it. */
    private fun View.progress(): Float = ReflectionUtils.readField(findViewById<View>(R.id.linearProgressBar), "progress") as Float

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
        ShadowLooper.idleMainLooper()
    }

    private fun deliveryProgressJson(
        deliveries: List<String> = emptyList(),
        endDate: String? = null,
    ): String {
        val fields =
            buildList {
                add(""""deliveries": [${deliveries.joinToString(",")}]""")
                endDate?.let { add(""""end_date": "$it"""") }
            }
        return "{${fields.joinToString(",")}}"
    }

    private fun approvedDeliveriesFor(
        unit: Int,
        count: Int,
        startId: Int = 1,
    ): List<String> = deliveriesJsonFor(unit, count, status = "approved", startId = startId)

    /** One visit per earlier day, so a unit can reach its total cap without hitting a daily cap. */
    private fun deliveriesJsonFor(
        unit: Int,
        count: Int,
        status: String,
        startId: Int = 1,
    ): List<String> =
        (0 until count).map { index ->
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(index + 1)) }.time
            """
            {
                "id": ${startId + index},
                "visit_date": "${visitDateFormat.format(date)}",
                "status": "$status",
                "deliver_unit_name": "Unit $unit",
                "deliver_unit_slug": "unit-$unit",
                "entity_id": "entity-${startId + index}",
                "entity_name": "Entity ${startId + index}",
                "reason": "",
                "deliver_unit_slug_id": "unit-$unit"
            }
            """.trimIndent()
        }

    private fun endDateDaysFromToday(offset: Int): String =
        endDateFormat.format(
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time,
        )

    /**
     * Writes the opportunity through the real storage layer so the repository's cache read and the
     * tab both see the same record production would.
     */
    private fun seedDeliveryJob(): ConnectJobRecord {
        val seeded =
            ConnectLearnJobTestData.job().apply {
                status = ConnectJobRecord.STATUS_DELIVERING
            }
        ConnectJobUtils.storeJobs(appContext, listOf(seeded), true)
        return ConnectJobUtils.getCompositeJob(ConnectLearnJobTestData.JOB_UUID)!!
    }

    /**
     * Writes a real user carrying a live Connect token so the progress call skips the SSO
     * round-trip. [ConnectDatabaseHelper.dbExists] has to be stubbed because it probes for the
     * on-disk connect db, which never exists under the in-memory test open helper.
     */
    private fun seedConnectUser() {
        mockkStatic(ConnectDatabaseHelper::class)
        every { ConnectDatabaseHelper.dbExists() } returns true

        val user =
            ConnectUserRecord(
                "1234567890",
                "test-user-id",
                "password",
                "Test User",
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

    private companion object {
        const val DELIVERY_PROGRESS_PATH = "/delivery_progress"
        const val SYNC_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 20L
        const val SCREEN_WIDTH_PX = 1080
        const val SCREEN_HEIGHT_PX = 1920
    }
}
