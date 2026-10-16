package org.commcare.fragments.connect

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectConstants
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUnlocker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Routing [ConnectUnlockFragment] performs once PersonalID has unlocked and opportunities are fetched. */
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectUnlockFragmentTest {
    private val uuid = "opp-uuid-1234"
    private val app = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus

    @Before
    fun setUp() {
        val manager = PersonalIdManager.getInstance()
        savedStatus = manager.status
        manager.status = PersonalIdManager.PersonalIdStatus.LoggedIn

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.reportConnectTabChange(any()) } returns Unit
        every { FirebaseAnalyticsUtil.reportExternalAppLaunchEvent(any(), any(), any()) } returns Unit
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) = Unit
            }

        val job = mockk<ConnectJobRecord>(relaxed = true)
        every { job.status } returns ConnectJobRecord.STATUS_DELIVERING
        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.getCompositeJob(eq(uuid)) } returns job
        every { ConnectJobUtils.getCompositeJobs(any(), any()) } returns emptyList()
        every { ConnectJobUtils.getPaymentsSortedByDate(any()) } returns emptyList()

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        mockkObject(PersonalIdUnlocker)
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<PersonalIdManager.ConnectActivityCompleteListener>().connectActivityComplete(true)
        }

        val repository = mockk<ConnectRepository>(relaxed = true)
        every { repository.retrieveOpportunitiesForJava(any()) } answers {
            firstArg<ConnectActivityCompleteListener>().connectActivityComplete(true, null)
        }
        every { repository.getDeliveryProgress(any(), any(), any()) } returns flowOf(DataState.Loading)
        every { repository.getOpportunities(any(), any()) } returns emptyFlow()
        mockkObject(ConnectRepository.Companion)
        every { ConnectRepository.getInstance() } returns repository

        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false
    }

    @After
    fun tearDown() {
        PersonalIdManager.getInstance().status = savedStatus
        unmockkAll()
    }

    @Test
    fun `an opportunity summary link opens opportunity home on the dashboard`() =
        assertUnlockOpensOpportunityHome(
            ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE,
            ConnectDeliveryHomeFragment.TAB_DASHBOARD,
        )

    @Test
    fun `a learn progress link opens opportunity home on the dashboard`() =
        assertUnlockOpensOpportunityHome(
            ConnectConstants.CCC_DEST_LEARN_PROGRESS,
            ConnectDeliveryHomeFragment.TAB_DASHBOARD,
        )

    @Test
    fun `a delivery progress link opens opportunity home on the dashboard`() =
        assertUnlockOpensOpportunityHome(
            ConnectConstants.CCC_DEST_DELIVERY_PROGRESS,
            ConnectDeliveryHomeFragment.TAB_DASHBOARD,
        )

    @Test
    fun `a payments link opens opportunity home on the payment tab`() =
        assertUnlockOpensOpportunityHome(
            ConnectConstants.CCC_DEST_PAYMENTS,
            ConnectDeliveryHomeFragment.TAB_PAYMENT,
        )

    @Test
    fun `an unrecognised action falls back to the opportunities list`() {
        val navController = unlockWith("not_a_destination")

        assertEquals(R.id.connect_jobs_list_fragment, navController.currentDestination?.id)
        assertNull(navController.previousBackStackEntry)
    }

    private fun assertUnlockOpensOpportunityHome(
        redirectAction: String,
        expectedTab: Int,
    ) {
        val navController = unlockWith(redirectAction)

        assertEquals(R.id.opportunity_home_fragment, navController.currentDestination?.id)
        assertEquals(
            expectedTab,
            navController.currentBackStackEntry
                ?.arguments
                ?.getInt(ConnectDeliveryHomeFragment.TAB_POSITION, -1),
        )
        assertNull(navController.previousBackStackEntry)
    }

    /** Opens ConnectActivity on [redirectAction] and returns its nav controller once unlock has routed. */
    private fun unlockWith(redirectAction: String): NavController {
        val intent =
            Intent(app, ConnectActivity::class.java).apply {
                putExtra(ConnectConstants.REDIRECT_ACTION, redirectAction)
                putExtra(ConnectConstants.OPPORTUNITY_UUID, uuid)
            }
        val activity = Robolectric.buildActivity(ConnectActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        return (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect) as NavHostFragment)
            .navController
    }
}
