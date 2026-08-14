package org.commcare.fragments.connect

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.commcare.views.connect.ConnectInfoHalfCard
import org.commcare.views.connect.SemiCircleProgressBar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
 * Robolectric tests for [ConnectDeliveryDashboardFragment], driven end to end: the job is seeded
 * into the real Connect database, delivery progress arrives over the real networking stack from
 * [ConnectMockApiServer], and the dashboard is read through the tab it actually lives in.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectDeliveryDashboardFragmentTest {
    private lateinit var activity: ConnectActivity
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus
    private lateinit var job: ConnectJobRecord
    private val mockApi = ConnectMockApiServer()

    @Volatile
    private var deliveryProgressBody: String = "{}"

    private val visitDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
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

        // Seeded before the activity exists: the opportunity list screen syncs on resume and needs
        // a user in place, and the dashboard reads the job back out of the same database.
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

    /**
     * The layout names colour roles rather than colours, and an unresolved `?attr/` fails silently
     * at runtime rather than at build time, so the resolved values are asserted directly.
     */
    @Test
    fun `the dashboard resolves its colour roles from ConnectTheme`() {
        val view = launch(progressResponse()).requireView()

        assertEquals(
            ContextCompat.getColor(activity, R.color.cool_gray_800),
            view.findViewById<TextView>(R.id.delivery_job_title).currentTextColor,
        )
        assertEquals(
            ContextCompat.getColor(activity, R.color.cool_gray_900),
            view.findViewById<TextView>(R.id.delivery_expiry_value).currentTextColor,
        )
        assertEquals(
            ContextCompat.getColor(activity, R.color.connect_green),
            view.findViewById<TextView>(R.id.delivery_chip_label).currentTextColor,
        )
        assertEquals(
            "progress card content colour comes through the themed attribute",
            ContextCompat.getColor(activity, R.color.connect_dark_grey),
            view.findViewById<TextView>(R.id.progress_card_bar_label).currentTextColor,
        )
    }

    @Test
    fun `visit progress reflects the deliveries returned by the server`() {
        val dashboard = launch(progressResponse(deliveries = deliveriesToday(unit = 1, count = 2)))
        val view = dashboard.requireView()

        assertEquals(
            activity.getString(R.string.connect_delivery_visit_progress_title),
            view.findViewById<TextView>(R.id.progress_card_title).text.toString(),
        )
        assertEquals(
            "2 of ${ConnectLearnJobTestData.MAX_DAILY_VISITS}",
            view.findViewById<TextView>(R.id.progress_card_bar_count).text.toString(),
        )

        val semiCircle = view.findViewById<SemiCircleProgressBar>(R.id.progress_card_semi_circle)
        assertEquals(2, semiCircle.current)
        assertEquals(ConnectLearnJobTestData.MAX_VISITS, semiCircle.max)
        assertEquals(
            activity.getString(R.string.connect_delivery_total_visits_completed),
            semiCircle.descriptionText.toString(),
        )
    }

    @Test
    fun `a card is shown per payment unit plus the total earnings card`() {
        val dashboard = launch(progressResponse(deliveries = deliveriesToday(unit = 1, count = 2)))
        val cards = halfCards(dashboard)

        assertEquals(ConnectLearnJobTestData.PAYMENT_UNIT_COUNT + 1, cards.size)
        assertEquals("Unit 1", cards[0].titleText.toString())
        assertEquals("Unit 2", cards[1].titleText.toString())
        assertEquals(
            activity.getString(R.string.connect_delivery_total_earnings),
            cards.last().titleText.toString(),
        )
    }

    @Test
    fun `payment unit cards count only their own deliveries`() {
        val dashboard =
            launch(
                progressResponse(
                    deliveries = deliveriesToday(unit = 1, count = 2) + deliveriesToday(unit = 2, count = 1, startId = 50),
                ),
            )
        val cards = halfCards(dashboard)

        assertEquals("2", cards[0].valueText.toString())
        assertEquals("1", cards[1].valueText.toString())
    }

    @Test
    fun `the total earnings card shows the accrued payment`() {
        val dashboard = launch(progressResponse(paymentAccrued = 1700))
        val earnings = halfCards(dashboard).last()

        assertEquals("₹1700", earnings.valueText.toString())
        assertEquals(View.INVISIBLE, earnings.findViewById<View>(R.id.info_card_subtitle_text).visibility)
    }

    @Test
    fun `cards sit the same distance apart across a row as between rows`() {
        val dashboard = launch(progressResponse(deliveries = deliveriesToday(unit = 1, count = 1)))
        val cards = halfCards(dashboard)
        val expectedGap = activity.resources.getDimensionPixelSize(R.dimen.connect_space_lg)

        assertEquals("gap across a row", expectedGap, cards[1].left - cards[0].right)
        assertEquals("gap between rows", expectedGap, cards[2].top - cards[0].bottom)
    }

    @Test
    fun `the cards are not clickable and tapping one navigates nowhere`() {
        val dashboard = launch(progressResponse(deliveries = deliveriesToday(unit = 1, count = 1)))
        val unitCard = halfCards(dashboard)[0]

        assertFalse("Payment unit cards are read-only", unitCard.isClickable)

        activity.runOnUiThread { unitCard.performClick() }
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.connect_delivery_home_fragment, navController.currentDestination?.id)
    }

    @Test
    fun `no warning is shown while the worker can still earn`() {
        val dashboard = launch(progressResponse(deliveries = deliveriesToday(unit = 1, count = 1)))
        val view = dashboard.requireView()

        assertEquals(View.GONE, view.findViewById<View>(R.id.progress_card_info_message).visibility)
        assertEquals(accentColor(), halfCards(dashboard)[0].valueTextColor())
    }

    @Test
    fun `an ended job warns that the job has ended and grays the figures`() {
        val dashboard = launch(progressResponse(endDate = "2020-01-01"))
        val view = dashboard.requireView()

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.progress_card_info_message).visibility)
        assertEquals(
            activity.getString(R.string.connect_progress_warning_ended),
            view.findViewById<TextView>(R.id.progress_card_info_text).text.toString(),
        )
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.progress_card_info_icon).visibility)
        assertEquals(disabledColor(), halfCards(dashboard)[0].valueTextColor())
    }

    @Test
    fun `a suspended user grays the figures`() {
        val dashboard = launch(progressResponse(suspended = true))
        val view = dashboard.requireView()

        assertEquals(
            activity.getString(R.string.user_suspended),
            view.findViewById<TextView>(R.id.progress_card_info_text).text.toString(),
        )
        assertEquals(disabledColor(), halfCards(dashboard)[0].valueTextColor())
    }

    @Test
    fun `one unit at its daily limit grays only that unit's card`() {
        val dashboard =
            launch(
                progressResponse(
                    deliveries = deliveriesToday(unit = 1, count = ConnectLearnJobTestData.PAYMENT_UNIT_MAX_DAILY),
                ),
            )
        val view = dashboard.requireView()
        val cards = halfCards(dashboard)

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.progress_card_info_message).visibility)
        assertTrue(
            "Warning should name the exhausted unit",
            view.findViewById<TextView>(R.id.progress_card_info_text).text.contains("Unit 1"),
        )
        assertEquals("exhausted unit", disabledColor(), cards[0].valueTextColor())
        assertEquals("unit with visits left", accentColor(), cards[1].valueTextColor())
        assertEquals("total earnings", accentColor(), cards.last().valueTextColor())
        assertEquals(
            "daily visit count on the progress card stays live",
            accentColor(),
            view.findViewById<TextView>(R.id.progress_card_bar_count).currentTextColor,
        )
    }

    @Test
    fun `a unit out of visits for good grays only that unit's card`() {
        val perUnitTotal = ConnectLearnJobTestData.MAX_VISITS / ConnectLearnJobTestData.PAYMENT_UNIT_COUNT
        val dashboard =
            launch(progressResponse(deliveries = deliveriesOnPastDays(unit = 1, count = perUnitTotal)))
        val cards = halfCards(dashboard)

        assertEquals(perUnitTotal.toString(), cards[0].valueText.toString())
        assertEquals("exhausted unit", disabledColor(), cards[0].valueTextColor())
        assertEquals("unit with visits left", accentColor(), cards[1].valueTextColor())
    }

    @Test
    fun `every unit at its daily limit grays the figures`() {
        val perUnit = ConnectLearnJobTestData.PAYMENT_UNIT_MAX_DAILY
        val dashboard =
            launch(
                progressResponse(
                    deliveries =
                        deliveriesToday(unit = 1, count = perUnit) +
                            deliveriesToday(unit = 2, count = perUnit, startId = 50),
                ),
            )

        assertEquals(
            View.VISIBLE,
            dashboard.requireView().findViewById<View>(R.id.progress_card_info_message).visibility,
        )
        assertEquals(disabledColor(), halfCards(dashboard)[0].valueTextColor())
    }

    /**
     * Navigates to the delivery home tabs, answers the delivery-progress request the screen fires on
     * resume with [responseBody], and returns the dashboard tab once it has re-rendered.
     */
    private fun launch(responseBody: String): ConnectDeliveryDashboardFragment {
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
        val dashboard =
            home.childFragmentManager.fragments
                .filterIsInstance<ConnectDeliveryDashboardFragment>()
                .first()

        // The first pass instantiates the page; this one measures and positions the view it created,
        // so tests can assert on real bounds.
        layOutHierarchy()
        return dashboard
    }

    /**
     * The dashboard is a [androidx.viewpager2.widget.ViewPager2] page, so it is only instantiated
     * once the pager has been measured and laid out.
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
     * dashboard's response. An empty opportunities body is deliberate: a parsed empty list would
     * prune the seeded job out of the database.
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

    private fun halfCards(dashboard: ConnectDeliveryDashboardFragment): List<ConnectInfoHalfCard> {
        val grid = dashboard.requireView().findViewById<GridLayout>(R.id.delivery_progress_grid)
        return (0 until grid.childCount).map { index ->
            grid.getChildAt(index) as ConnectInfoHalfCard
        }
    }

    private fun ConnectInfoHalfCard.valueTextColor(): Int = findViewById<TextView>(R.id.info_card_value_text).currentTextColor

    private fun accentColor(): Int = ContextCompat.getColor(activity, R.color.connect_dark_blue_color)

    private fun disabledColor(): Int = ContextCompat.getColor(activity, R.color.connect_dark_grey)

    private fun progressResponse(
        deliveries: List<String> = emptyList(),
        paymentAccrued: Int? = null,
        endDate: String? = null,
        suspended: Boolean = false,
    ): String {
        val fields =
            buildList {
                add(""""deliveries": [${deliveries.joinToString(",")}]""")
                paymentAccrued?.let { add(""""payment_accrued": $it""") }
                endDate?.let { add(""""end_date": "$it"""") }
                if (suspended) add(""""is_user_suspended": true""")
            }
        return "{${fields.joinToString(",")}}"
    }

    private fun deliveriesToday(
        unit: Int,
        count: Int,
        startId: Int = 1,
    ): List<String> =
        (0 until count).map { index ->
            deliveryJson(id = startId + index, unit = unit, date = Date())
        }

    /** One visit per earlier day, so a unit reaches its total cap without hitting a daily cap. */
    private fun deliveriesOnPastDays(
        unit: Int,
        count: Int,
        startId: Int = 1,
    ): List<String> =
        (0 until count).map { index ->
            val date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(index + 1)) }.time
            deliveryJson(id = startId + index, unit = unit, date = date)
        }

    private fun deliveryJson(
        id: Int,
        unit: Int,
        date: Date,
    ): String =
        """
        {
            "id": $id,
            "visit_date": "${visitDateFormat.format(date)}",
            "status": "approved",
            "deliver_unit_name": "Unit $unit",
            "deliver_unit_slug": "unit-$unit",
            "entity_id": "entity-$id",
            "entity_name": "Entity $id",
            "reason": "",
            "deliver_unit_slug_id": "unit-$unit"
        }
        """.trimIndent()

    /**
     * Writes the opportunity through the real storage layer so the repository's cache read and the
     * dashboard both see the same record production would.
     */
    private fun seedDeliveryJob(): ConnectJobRecord {
        val seeded =
            ConnectLearnJobTestData.job().apply {
                status = ConnectJobRecord.STATUS_DELIVERING
            }
        ConnectJobUtils.storeJobs(appContext, listOf(seeded), true)
        return ConnectJobUtils.getCompositeJob(appContext, ConnectLearnJobTestData.JOB_UUID)!!
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
        ConnectUserDatabaseUtil.storeUser(appContext, user)
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
