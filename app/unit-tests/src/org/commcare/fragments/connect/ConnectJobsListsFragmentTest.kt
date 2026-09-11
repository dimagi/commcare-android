package org.commcare.fragments.connect

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.color.MaterialColors
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.util.ConnectTestUtils
import org.commcare.connect.ConnectLearnJobTestData
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.network.ConnectMockApiServer
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.ConnectRequestManager
import org.commcare.connect.repository.ConnectSyncPreferences
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.coroutines.DispatcherProvider
import org.commcare.views.connect.CircleProgressBar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Robolectric tests for the redesigned opportunity list, driven end to end: every opportunity is
 * seeded into the real Connect database and read back through the rows the adapter actually
 * renders, so the section a job lands in, its date label and its badge are all asserted from the
 * views rather than from the adapter's internals.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectJobsListsFragmentTest {
    private lateinit var activity: ConnectActivity
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus
    private val mockApi = ConnectMockApiServer()

    private val navController: NavController get() = navHostFragment.navController
    private val appContext get() = ApplicationProvider.getApplicationContext<CommCareTestApplication>()

    @Before
    fun setUp() {
        savedStatus = PersonalIdManager.getInstance().status
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.LoggedIn
        mockApi.start()
        mockApi.server.dispatcher = alwaysEmptyJsonDispatcher()
        ConnectRepository.resetInstance()
        ConnectRequestManager.cancelAll()

        mockkObject(DispatcherProvider)
        every { DispatcherProvider.io() } returns UnconfinedTestDispatcher()

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportConnectTabChange(any()) } returns Unit
        every { FirebaseAnalyticsUtil.reportCccApiStartLearning(any()) } returns Unit
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) = Unit
            }

        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns true

        // Seeded before the activity exists: the list is the start destination and reads each
        // job's composite record straight back out of the database as it binds.
        ConnectTestUtils.signInToPersonalId(hasConnectAccess = true)
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.LoggedIn
        seedOpportunities()

        // Record a just-completed sync so the offline-first flow serves its cache and skips the
        // network entirely: the real fetch needs a Connect session and crashes background
        // coroutines under Robolectric, which masks every assertion in the suite.
        ConnectSyncPreferences.getInstance().apply {
            markSessionStart()
            storeLastSyncTime(ConnectRepository.SYNC_KEY_OPPORTUNITIES)
        }

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

        ShadowLooper.idleMainLooper()
        measureAndLayoutScreen()
    }

    @After
    fun tearDown() {
        ConnectDatabaseHelper.teardown()
        PersonalIdManager.getInstance().status = savedStatus
        mockApi.shutdown()
        unmockkAll()
    }

    // ---------- Sections ----------

    @Test
    fun `the list is split into in progress, new and completed or expired sections`() {
        assertEquals(
            listOf(
                activity.getString(R.string.connect_in_progress),
                activity.getString(R.string.connect_new_opportunities),
                activity.getString(R.string.connect_completed_expired_label),
            ),
            getSectionHeaderTitles(),
        )
    }

    // ---------- Date label ----------

    @Test
    fun `a running opportunity is labelled with its expiry`() {
        assertEquals(
            activity.getString(R.string.connect_label_expiry),
            getDateLabelText(LEARNING_UUID),
        )
    }

    @Test
    fun `a new opportunity is labelled with its expiry`() {
        assertEquals(
            activity.getString(R.string.connect_label_expiry),
            getDateLabelText(NEW_UUID),
        )
    }

    @Test
    fun `a finished opportunity the worker completed is labelled completed on`() {
        assertEquals(
            activity.getString(R.string.connect_label_completed_on),
            getDateLabelText(COMPLETED_UUID),
        )
    }

    @Test
    fun `a finished opportunity the worker did not complete is labelled expired on`() {
        assertEquals(
            activity.getString(R.string.connect_label_expired_on),
            getDateLabelText(EXPIRED_UUID),
        )
    }

    // ---------- Expiry warning ----------

    @Test
    fun `an opportunity expiring within five days shows the alert icon and a negative date`() {
        val row = getRowForJobUuid(EXPIRING_SOON_UUID)

        assertEquals(View.VISIBLE, row.findViewById<ImageView>(R.id.ivInfo).visibility)
        assertEquals(
            roleColour(row, R.attr.connectStatusNegative),
            row.findViewById<TextView>(R.id.tvDate).currentTextColor,
        )
    }

    @Test
    fun `an opportunity expiring later hides the alert icon and keeps a muted date`() {
        val row = getRowForJobUuid(LEARNING_UUID)

        assertEquals(View.GONE, row.findViewById<ImageView>(R.id.ivInfo).visibility)
        assertEquals(
            roleColour(row, R.attr.connectOnSurfaceVariant),
            row.findViewById<TextView>(R.id.tvDate).currentTextColor,
        )
    }

    // ---------- Badge ----------

    @Test
    fun `only a running opportunity draws the progress ring`() {
        assertEquals(View.VISIBLE, getProgressRing(LEARNING_UUID).visibility)
        assertEquals(View.VISIBLE, getProgressRing(EXPIRING_SOON_UUID).visibility)
        assertEquals(View.INVISIBLE, getProgressRing(NEW_UUID).visibility)
        assertEquals(View.INVISIBLE, getProgressRing(COMPLETED_UUID).visibility)
        assertEquals(View.INVISIBLE, getProgressRing(EXPIRED_UUID).visibility)
    }

    @Test
    fun `each state shows its own badge icon`() {
        assertEquals(R.drawable.ic_connect_learning, getBadgeDrawableRes(LEARNING_UUID))
        assertEquals(R.drawable.ic_connect_delivery, getBadgeDrawableRes(EXPIRING_SOON_UUID))
        assertEquals(R.drawable.ic_connect_new_opportunity, getBadgeDrawableRes(NEW_UUID))
        assertEquals(R.drawable.ic_connect_completed_badge, getBadgeDrawableRes(COMPLETED_UUID))
        assertEquals(R.drawable.ic_connect_expired_badge, getBadgeDrawableRes(EXPIRED_UUID))
    }

    // ---------- Card ----------

    @Test
    fun `tapping a new opportunity opens the opportunity intro`() {
        activity.runOnUiThread { getRowForJobUuid(NEW_UUID).performClick() }
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.connect_job_intro_fragment, navController.currentDestination?.id)
    }

    @Test
    fun `the card resolves its colour roles from ConnectTheme`() {
        val row = getRowForJobUuid(LEARNING_UUID)

        assertEquals(
            roleColour(row, R.attr.connectOnSurfaceEmphasis),
            row.findViewById<TextView>(R.id.tvTitle).currentTextColor,
        )
        assertEquals(
            roleColour(row, R.attr.connectOnSurfaceMuted),
            row.findViewById<TextView>(R.id.tvDateLabel).currentTextColor,
        )
        assertEquals(
            roleColour(row, R.attr.connectOnSurfaceVariant),
            row.findViewById<TextView>(R.id.tvDate).currentTextColor,
        )
    }

    // ---------- Fixtures ----------

    private fun seedOpportunities() {
        val jobs =
            listOf(
                opportunity(LEARNING_UUID, 1, "Learning Opportunity", ConnectJobRecord.STATUS_LEARNING, FUTURE_DATE),
                opportunity(
                    EXPIRING_SOON_UUID,
                    2,
                    "Expiring Soon Opportunity",
                    ConnectJobRecord.STATUS_DELIVERING,
                    daysFromNow(2),
                ),
                opportunity(NEW_UUID, 3, "New Opportunity", ConnectJobRecord.STATUS_AVAILABLE_NEW, FUTURE_DATE),
                opportunity(
                    COMPLETED_UUID,
                    4,
                    "Completed Opportunity",
                    ConnectJobRecord.STATUS_DELIVERING,
                    PAST_DATE,
                ).apply { completedVisits = ConnectLearnJobTestData.MAX_VISITS },
                opportunity(EXPIRED_UUID, 5, "Expired Opportunity", ConnectJobRecord.STATUS_DELIVERING, PAST_DATE),
            )
        ConnectJobUtils.storeJobs(appContext, jobs, true)
    }

    private fun opportunity(
        uuid: String,
        id: Int,
        title: String,
        status: Int,
        endDate: String,
    ): ConnectJobRecord =
        ConnectLearnJobTestData.job(endDate = endDate).apply {
            jobUUID = uuid
            jobId = id
            this.title = title
            this.status = status
        }

    // ---------- View access ----------

    private val jobListRecyclerView: RecyclerView get() = activity.findViewById(R.id.rvJobList)

    /** The adapter tags each row with its opportunity, which is the only stable row identity. */
    private fun getRowForJobUuid(uuid: String): View =
        (0 until jobListRecyclerView.childCount)
            .map { jobListRecyclerView.getChildAt(it) }
            .firstOrNull { it.tag == "opp_uuid: $uuid" }
            ?: throw AssertionError("No row rendered for $uuid")

    private fun getRowsContainingView(childId: Int): List<View> =
        (0 until jobListRecyclerView.childCount)
            .map { jobListRecyclerView.getChildAt(it) }
            .filter { it.findViewById<View>(childId) != null }

    private fun getSectionHeaderTitles(): List<String> =
        getRowsContainingView(R.id.tv_section_header)
            .map { it.findViewById<TextView>(R.id.tv_section_header).text.toString() }

    private fun getDateLabelText(uuid: String): String = getRowForJobUuid(uuid).findViewById<TextView>(R.id.tvDateLabel).text.toString()

    private fun getProgressRing(uuid: String): CircleProgressBar = getRowForJobUuid(uuid).findViewById(R.id.progressBar)

    private fun getBadgeImageView(uuid: String): ImageView = getRowForJobUuid(uuid).findViewById(R.id.imgJobType)

    /** The colour the row's own theme gives a role, so assertions survive a palette change. */
    private fun roleColour(
        row: View,
        attr: Int,
    ): Int = MaterialColors.getColor(row, attr)

    private fun getBadgeDrawableRes(uuid: String): Int = Shadows.shadowOf(getBadgeImageView(uuid).drawable).createdFromResId

    private fun measureAndLayoutScreen() {
        val root = activity.window.decorView
        root.measure(
            View.MeasureSpec.makeMeasureSpec(SCREEN_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SCREEN_HEIGHT_PX, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, SCREEN_WIDTH_PX, SCREEN_HEIGHT_PX)
        ShadowLooper.idleMainLooper()
    }

    /**
     * An empty body is deliberate: a parsed empty opportunity list would prune the seeded jobs
     * back out of the database.
     */
    private fun alwaysEmptyJsonDispatcher(): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setResponseCode(200).setBody("{}")
        }

    private fun daysFromNowDate(days: Int): Date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, days) }.time

    private fun daysFromNow(days: Int): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(daysFromNowDate(days))

    private companion object {
        const val LEARNING_UUID = "job-uuid-learning"
        const val EXPIRING_SOON_UUID = "job-uuid-expiring-soon"
        const val NEW_UUID = "job-uuid-new"
        const val COMPLETED_UUID = "job-uuid-completed"
        const val EXPIRED_UUID = "job-uuid-expired"

        const val FUTURE_DATE = "2030-12-31"
        const val PAST_DATE = "2025-06-01"

        const val SCREEN_WIDTH_PX = 1080
        const val SCREEN_HEIGHT_PX = 1920
    }
}
