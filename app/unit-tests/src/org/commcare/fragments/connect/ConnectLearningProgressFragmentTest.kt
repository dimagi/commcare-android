package org.commcare.fragments.connect

import android.content.Context
import android.os.Build
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.commcare.connect.repository.ConnectRequestManager
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.coroutines.DispatcherProvider
import org.commcare.views.connect.ConnectSuccessFailureCard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Calendar
import java.util.Date

/**
 * Robolectric tests for [ConnectLearningProgressFragment]: verifies which state the screen renders
 * before any refresh lands, and drives the delivery CTA through a real click so the claim call,
 * local status update and navigation are all covered end to end.
 *
 * The claim request goes through the real networking stack against [ConnectMockApiServer]; the
 * seeded user carries a live Connect token so the call skips the SSO round-trip.
 */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectLearningProgressFragmentTest {
    private lateinit var activity: ConnectActivity
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus
    private val mockApi = ConnectMockApiServer()

    private val navController: NavController get() = navHostFragment.navController

    @Before
    fun setUp() {
        savedStatus = PersonalIdManager.getInstance().status
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.LoggedIn
        seedConnectUser(ApplicationProvider.getApplicationContext())

        mockApi.start()
        ConnectRepository.resetInstance()
        ConnectRequestManager.cancelAll()

        mockkObject(DispatcherProvider)
        every { DispatcherProvider.io() } returns UnconfinedTestDispatcher()

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns mockk(relaxed = true)
        every { FirebaseAnalyticsUtil.reportCccApiClaimJob(any()) } just Runs
        every { FirebaseAnalyticsUtil.reportCccApiLearnProgress(any()) } just Runs

        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false

        // Pre-enqueue a response so the getOpportunities request made by the start destination
        // does not hang, then drain it so it doesn't sit ahead of later requests in the queue.
        mockApi.server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

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

        mockApi.drainHttp()
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        PersonalIdManager.getInstance().status = savedStatus
        mockApi.shutdown()
        unmockkAll()
    }

    @Test
    fun `learn complete state is shown before any refresh emission`() {
        val fragment = launch(ConnectLearnJobTestData.job())
        val view = fragment.requireView()

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.learnCompleteView).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.learnProgressView).visibility)
    }

    @Test
    fun `progress state is shown while learning is incomplete`() {
        val fragment =
            launch(ConnectLearnJobTestData.job(completedModules = 1, assessmentScore = null))
        val view = fragment.requireView()

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.learnProgressView).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.learnCompleteView).visibility)
    }

    @Test
    fun `progress state is shown when the assessment was failed`() {
        val fragment = launch(ConnectLearnJobTestData.job(assessmentScore = 10))
        val view = fragment.requireView()

        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.learnProgressView).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.learnCompleteView).visibility)
    }

    @Test
    fun `tapping the progress cta routes to the learn download when the learn app is missing`() {
        val fragment =
            launch(ConnectLearnJobTestData.job(completedModules = 1, assessmentScore = null))

        val ctaButton = learnProgressCta(fragment)
        activity.runOnUiThread { ctaButton.performClick() }
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.connect_downloading_fragment, navController.currentDestination?.id)

        // The delivery download reaches the same destination, so the arguments are what distinguish
        // the two: this path must ask for the learn app.
        val args = navController.currentBackStackEntry?.arguments
        assertEquals(
            activity.getString(R.string.connect_downloading_learn),
            args?.getString("title"),
        )
        assertTrue("Should download the learn app, not the delivery app", args!!.getBoolean("learning"))
        assertEquals("Progress CTA should not claim the job", 0, mockApi.requestCount)
    }

    @Test
    fun `tapping the cta claims the job and navigates to the delivery download`() {
        val job = ConnectLearnJobTestData.job()
        val fragment = launch(job)

        clickCta(fragment)
        val request = respondToClaim(responseCode = 200)

        assertEquals("POST", request.method)
        assertEquals("/api/opportunity/${job.jobUUID}/claim", request.path)
        assertEquals(ConnectJobRecord.STATUS_DELIVERING, job.status)
        assertEquals(
            ConnectJobRecord.STATUS_DELIVERING,
            ConnectJobUtils.getCompositeJob(activity, job.jobUUID)?.status,
        )
        assertEquals(R.id.connect_downloading_fragment, navController.currentDestination?.id)
    }

    @Test
    fun `tapping the cta navigates to delivery home when the delivery app is installed`() {
        every { AppUtils.isAppInstalled(ConnectLearnJobTestData.DELIVERY_APP_ID) } returns true
        val job = ConnectLearnJobTestData.job()
        val fragment = launch(job)

        clickCta(fragment)
        respondToClaim(responseCode = 200)

        assertEquals(R.id.connect_delivery_home_fragment, navController.currentDestination?.id)
    }

    @Test
    fun `an already claimed job skips the claim call and navigates straight on`() {
        val job = ConnectLearnJobTestData.job()
        job.status = ConnectJobRecord.STATUS_DELIVERING
        val fragment = launch(job)
        val requestsBefore = mockApi.server.requestCount

        clickCta(fragment)

        assertEquals("No claim request should be sent", requestsBefore, mockApi.server.requestCount)
        assertEquals(R.id.connect_downloading_fragment, navController.currentDestination?.id)
    }

    @Test
    fun `a failed claim shows the failure card, re-enables the cta and stays on the screen`() {
        val job = ConnectLearnJobTestData.job()
        val fragment = launch(job)
        val ctaButton = learnCompleteCta(fragment)

        clickCta(fragment)
        assertEquals("CTA should be disabled while claiming", false, ctaButton.isEnabled)

        respondToClaim(responseCode = 400)

        val failureCard =
            fragment.requireView().findViewById<ConnectSuccessFailureCard>(
                R.id.learn_complete_failure_card,
            )
        assertEquals(View.VISIBLE, failureCard.visibility)
        assertEquals(
            activity.getString(R.string.recovery_unable_to_claim_opportunity),
            failureCard.messageText.toString(),
        )
        assertTrue("CTA should be re-enabled after a failure", ctaButton.isEnabled)
        assertEquals(
            R.id.connect_job_learning_progress_fragment,
            navController.currentDestination?.id,
        )
        assertEquals(ConnectJobRecord.STATUS_LEARNING, job.status)
    }

    private fun launch(job: ConnectJobRecord): ConnectLearningProgressFragment {
        activity.setActiveJob(job)
        // Pre-enqueue a 400 for getLearningProgress so it doesn't hang; a 400 error leaves the
        // seeded job state intact (the observer ignores DataState.Error updates to the job field).
        mockApi.server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))
        activity.runOnUiThread {
            navController.navigate(
                R.id.action_connect_jobs_list_fragment_to_connect_job_learning_progress_fragment,
            )
        }
        ShadowLooper.idleMainLooper()
        // Drain the getLearningProgress request so it doesn't sit ahead of the claim request in the queue.
        mockApi.drainHttp()
        ShadowLooper.idleMainLooper()
        return navHostFragment.childFragmentManager.primaryNavigationFragment
            as ConnectLearningProgressFragment
    }

    private fun clickCta(fragment: ConnectLearningProgressFragment) {
        val ctaButton = learnCompleteCta(fragment)
        activity.runOnUiThread { ctaButton.performClick() }
        ShadowLooper.idleMainLooper()
    }

    /**
     * Both the progress and the complete view carry a [org.commcare.views.connect.ConnectCtaBar], so
     * the lookup is scoped to the complete view rather than resolved from the fragment root.
     */
    private fun learnCompleteCta(fragment: ConnectLearningProgressFragment): MaterialButton =
        fragment
            .requireView()
            .findViewById<View>(R.id.learnCompleteView)
            .findViewById(R.id.cta_button)

    /** The progress view's own CTA, scoped for the same reason as [learnCompleteCta]. */
    private fun learnProgressCta(fragment: ConnectLearningProgressFragment): MaterialButton =
        fragment
            .requireView()
            .findViewById<View>(R.id.learnProgressView)
            .findViewById(R.id.cta_button)

    /**
     * Answers the pending claim request with [responseCode] and drains the response callback,
     * returning the request the fragment actually sent.
     */
    private fun respondToClaim(responseCode: Int): RecordedRequest {
        mockApi.server.enqueue(MockResponse().setResponseCode(responseCode).setBody("{}"))
        val request = mockApi.drainHttp()
        ShadowLooper.idleMainLooper()
        return request
    }

    /**
     * Writes a real user through the Connect storage layer so the learn-complete view can read the
     * learner name and the claim call finds a live token instead of making an SSO round-trip.
     * [ConnectDatabaseHelper.dbExists] has to be stubbed because it probes for the on-disk connect
     * db, which never exists under the in-memory test open helper.
     */
    private fun seedConnectUser(context: Context = activity) {
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
        ConnectUserDatabaseUtil.storeUser(context, user)
    }

    private fun tomorrow(): Date = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.time
}
