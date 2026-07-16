package org.commcare.fragments.connect

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectConstants
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.rules.MainCoroutineRule
import org.commcare.views.connect.ConnectCtaBar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectDeliveryOppHomeFragmentTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val uuid = "opp-uuid-1234"
    private val app = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus
    private lateinit var job: ConnectJobRecord

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

        job = mockk(relaxed = true)
        every { job.status } returns ConnectJobRecord.STATUS_DELIVERING

        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.getCompositeJob(any(), eq(uuid)) } returns job
        every { ConnectJobUtils.getPaymentsSortedByDate(any()) } returns emptyList()

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        val mockRepository = mockk<ConnectRepository>(relaxed = true)
        every { mockRepository.getDeliveryProgress(any(), any(), any()) } returns flowOf(DataState.Loading)
        mockkObject(ConnectRepository.Companion)
        every { ConnectRepository.getInstance(any()) } returns mockRepository

        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false
    }

    @After
    fun tearDown() {
        PersonalIdManager.getInstance().status = savedStatus
        unmockkAll()
    }

    @Test
    fun `shows the Dashboard, Payment, Visits and More tabs`() {
        val oppHome = launchOppHome()
        val tabLayout = oppHome.requireView().findViewById<TabLayout>(R.id.connect_delivery_opp_home_tabs)

        assertEquals(4, tabLayout.tabCount)
        assertEquals(app.getString(R.string.connect_dashboard), tabLayout.getTabAt(0)?.text)
        assertEquals(app.getString(R.string.connect_payment), tabLayout.getTabAt(1)?.text)
        assertEquals(app.getString(R.string.connect_visits), tabLayout.getTabAt(2)?.text)
        assertEquals(app.getString(R.string.connect_more), tabLayout.getTabAt(3)?.text)
    }

    @Test
    fun `footer CTA bar and its start button are visible on the home screen`() {
        val oppHome = launchOppHome()
        val ctaBar =
            oppHome.requireView().findViewById<ConnectCtaBar>(R.id.connect_delivery_cta_bar)
        val ctaButton = ctaBar.findViewById<MaterialButton>(R.id.cta_button)

        assertEquals(View.VISIBLE, ctaBar.visibility)
        assertEquals(View.VISIBLE, ctaButton.visibility)
    }

    @Test
    fun `selecting a tab switches the pager to that page`() {
        val oppHome = launchOppHome()
        val view = oppHome.requireView()
        val tabLayout = view.findViewById<TabLayout>(R.id.connect_delivery_opp_home_tabs)
        val viewPager = view.findViewById<ViewPager2>(R.id.connect_delivery_opp_home_view_pager)

        tabLayout.getTabAt(ConnectDeliveryOppHomeFragment.TAB_VISITS)?.select()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(ConnectDeliveryOppHomeFragment.TAB_VISITS, viewPager.currentItem)
    }

    @Test
    fun `clicking Start when the app is not installed navigates to the downloading screen`() {
        val oppHome = launchOppHome()
        val startButton =
            oppHome.requireView().findViewById<MaterialButton>(R.id.cta_button)

        startButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val navController = NavHostFragment.findNavController(oppHome)
        assertEquals(R.id.connect_downloading_fragment, navController.currentDestination?.id)
    }

    private fun launchOppHome(): ConnectDeliveryOppHomeFragment {
        val intent =
            Intent(app, ConnectActivity::class.java).apply {
                putExtra(ConnectConstants.GO_TO_JOB_STATUS, true)
                putExtra(ConnectConstants.OPPORTUNITY_UUID, uuid)
            }
        val activity = Robolectric.buildActivity(ConnectActivity::class.java, intent).setup().get()
        shadowOf(Looper.getMainLooper()).idle()

        val navHost =
            activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect)
                as NavHostFragment
        assertTrue(
            "Expected to land on the OppHome destination",
            navHost.navController.currentDestination?.id == R.id.connect_delivery_opp_home_fragment,
        )
        return navHost.childFragmentManager.fragments
            .filterIsInstance<ConnectDeliveryOppHomeFragment>()
            .first()
    }
}
