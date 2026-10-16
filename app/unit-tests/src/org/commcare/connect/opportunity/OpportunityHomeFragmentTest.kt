package org.commcare.connect.opportunity

import android.os.Build
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectAppLauncher
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.AppInstallState
import org.commcare.connect.viewmodel.AppInstallTarget
import org.commcare.connect.viewmodel.ConnectAppInstallViewModel
import org.commcare.dalvik.R
import org.commcare.fragments.connect.BaseConnectJobIntroTest
import org.commcare.fragments.connect.ConnectDeliveryHomeFragment
import org.commcare.fragments.connect.ConnectJobIntroFragment
import org.commcare.fragments.connect.ConnectLearningProgressFragment
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class OpportunityHomeFragmentTest : BaseConnectJobIntroTest() {
    private val repository get() = ConnectRepository.getInstance()

    @Before
    override fun setUp() {
        super.setUp()
        every { repository.getLearningProgress(any(), any(), any()) } returns emptyFlow()
        every { repository.getDeliveryProgress(any(), any(), any()) } returns emptyFlow()
    }

    private fun openPage(args: Bundle? = null): OpportunityHomeFragment {
        activity.runOnUiThread { navController.navigate(R.id.opportunity_home_fragment, args) }
        ShadowLooper.idleMainLooper()
        return navHostFragment.childFragmentManager.fragments
            .filterIsInstance<OpportunityHomeFragment>()
            .single()
    }

    @Test
    fun `an unaccepted opportunity opens on the job intro`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE

        openPage()

        navHostFragment.opportunityHomeSurface<ConnectJobIntroFragment>()
    }

    @Test
    fun `a learning opportunity opens on learn progress`() {
        job.status = ConnectJobRecord.STATUS_LEARNING

        openPage()

        navHostFragment.opportunityHomeSurface<ConnectLearningProgressFragment>()
    }

    @Test
    fun `a delivering opportunity opens on the tab the link asked for`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING

        openPage(
            Bundle().apply {
                putInt(ConnectDeliveryHomeFragment.TAB_POSITION, ConnectDeliveryHomeFragment.TAB_PAYMENT)
            },
        )

        val delivery = navHostFragment.opportunityHomeSurface<ConnectDeliveryHomeFragment>()
        assertEquals(
            ConnectDeliveryHomeFragment.TAB_PAYMENT,
            delivery.requireArguments().getInt(ConnectDeliveryHomeFragment.TAB_POSITION),
        )
    }

    @Test
    fun `a phase change swaps the surface in place`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        val page = openPage()

        job.status = ConnectJobRecord.STATUS_LEARNING
        activity.runOnUiThread { page.onPhaseChanged() }
        ShadowLooper.idleMainLooper()

        navHostFragment.opportunityHomeSurface<ConnectLearningProgressFragment>()
        assertEquals(R.id.opportunity_home_fragment, navController.currentDestination?.id)
        assertEquals(1, page.childFragmentManager.fragments.size)
    }

    @Test
    fun `opening a learning opportunity reports the learn progress screen`() {
        job.status = ConnectJobRecord.STATUS_LEARNING

        openPage()

        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportScreenView(
                "fragment_connect_learning_progress",
                ConnectLearningProgressFragment::class.java.name,
            )
        }
    }

    @Test
    fun `a phase change reports each surface once`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        val page = openPage()

        job.status = ConnectJobRecord.STATUS_LEARNING
        activity.runOnUiThread { page.onPhaseChanged() }
        ShadowLooper.idleMainLooper()

        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportScreenView("fragment_connect_job_intro", ConnectJobIntroFragment::class.java.name)
        }
        verify(exactly = 1) {
            FirebaseAnalyticsUtil.reportScreenView(
                "fragment_connect_learning_progress",
                ConnectLearningProgressFragment::class.java.name,
            )
        }
        verifyOrder {
            FirebaseAnalyticsUtil.reportScreenView("fragment_connect_job_intro", any())
            FirebaseAnalyticsUtil.reportScreenView("fragment_connect_learning_progress", any())
        }
    }

    @Test
    fun `resuming the activity reports the current surface again`() {
        job.status = ConnectJobRecord.STATUS_LEARNING
        openPage()

        activity.runOnUiThread { activityController.pause().stop() }
        ShadowLooper.idleMainLooper()
        activity.runOnUiThread { activityController.start().resume() }
        ShadowLooper.idleMainLooper()

        verify(exactly = 2) {
            FirebaseAnalyticsUtil.reportScreenView(
                "fragment_connect_learning_progress",
                ConnectLearningProgressFragment::class.java.name,
            )
        }
    }

    @Test
    fun `returning from a sub-destination reports the surface again`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        openVisitsDetailAndReturn(openPage())

        verify(exactly = 2) {
            FirebaseAnalyticsUtil.reportScreenView(
                "fragment_connect_delivery_home",
                ConnectDeliveryHomeFragment::class.java.name,
            )
        }
    }

    /** The nav listener's own skip is stubbed out in [BaseConnectJobIntroTest.setUp]; this covers only the page's own reporting. */
    @Test
    fun `the page's own reporting never includes itself as a screen`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        openVisitsDetailAndReturn(openPage())
        activity.runOnUiThread {
            activityController
                .pause()
                .stop()
                .start()
                .resume()
        }
        ShadowLooper.idleMainLooper()

        verify(exactly = 0) { FirebaseAnalyticsUtil.reportScreenView("fragment_opportunity_home", any()) }
        verify(exactly = 0) { FirebaseAnalyticsUtil.reportScreenView(any(), OpportunityHomeFragment::class.java.name) }
    }

    private fun openVisitsDetailAndReturn(page: OpportunityHomeFragment) {
        activity.runOnUiThread {
            page.navigateFromPage(
                OpportunityHomeFragmentDirections.actionOpportunityHomeFragmentToConnectDeliveryVisitsDetailFragment(
                    "unit-1",
                ),
            )
        }
        ShadowLooper.idleMainLooper()
        activity.runOnUiThread { navController.popBackStack() }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `a phase change while the page is stopped waits for the next resume`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        val page = openPage()

        activity.runOnUiThread { activityController.pause().stop() }
        ShadowLooper.idleMainLooper()

        job.status = ConnectJobRecord.STATUS_LEARNING
        activity.runOnUiThread { page.onPhaseChanged() }
        ShadowLooper.idleMainLooper()

        activity.runOnUiThread { activityController.start().resume() }
        ShadowLooper.idleMainLooper()

        navHostFragment.opportunityHomeSurface<ConnectLearningProgressFragment>()
    }

    @Test
    fun `returning from a sub-destination restores the surface`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        val page = openPage()

        activity.runOnUiThread {
            page.navigateFromPage(
                OpportunityHomeFragmentDirections.actionOpportunityHomeFragmentToConnectDeliveryVisitsDetailFragment(
                    "unit-1",
                ),
            )
        }
        ShadowLooper.idleMainLooper()

        activity.runOnUiThread { navController.popBackStack() }
        ShadowLooper.idleMainLooper()

        val deliveryFragments =
            page.childFragmentManager.fragments.filterIsInstance<ConnectDeliveryHomeFragment>()
        assertEquals(1, deliveryFragments.size)
        assertEquals(R.id.opportunity_home_fragment, navController.currentDestination?.id)
    }

    @Test
    fun `refresh reaches the surface being shown`() {
        job.status = ConnectJobRecord.STATUS_LEARNING
        val page = openPage()

        activity.runOnUiThread { page.refresh(true) }
        ShadowLooper.idleMainLooper()

        verify { repository.getLearningProgress(any(), true, any()) }
    }

    @Test
    fun `a page action is ignored once the page has been left`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        val page = openPage()
        val directions =
            OpportunityHomeFragmentDirections.actionOpportunityHomeFragmentToConnectLearnModulesBottomSheet()

        activity.runOnUiThread {
            page.navigateFromPage(directions)
            page.navigateFromPage(directions)
        }
        ShadowLooper.idleMainLooper()
        assertEquals(R.id.connect_learn_modules_bottom_sheet, navController.currentDestination?.id)

        activity.runOnUiThread { navController.popBackStack() }
        ShadowLooper.idleMainLooper()
        assertEquals(R.id.opportunity_home_fragment, navController.currentDestination?.id)
    }

    @Test
    fun `a lock during an installed app's launch keeps the intro until the launch is over`() {
        val intro = startLaunchOfInstalledLearnApp()

        activity.runOnUiThread { activityController.pause().stop() }
        ShadowLooper.idleMainLooper()
        activity.runOnUiThread { activityController.start().resume() }
        ShadowLooper.idleMainLooper()

        assertSame(intro, navHostFragment.opportunityHomeSurface<ConnectJobIntroFragment>())
        assertEquals(AppInstallState.Launching, installViewModel().installState.value)
    }

    @Test
    fun `a launch cancelled with the screen's view leaves nothing in flight`() {
        startLaunchOfInstalledLearnApp()

        activity.runOnUiThread { navController.popBackStack() }
        ShadowLooper.idleMainLooper()

        assertEquals(AppInstallState.Idle, installViewModel().installState.value)
    }

    @Test
    fun `an install another screen started does not keep the page from showing a surface`() {
        installViewModel().launch(AppInstallTarget("other-app", true, "other-screen"))
        job.status = ConnectJobRecord.STATUS_LEARNING

        openPage()

        navHostFragment.opportunityHomeSurface<ConnectLearningProgressFragment>()
    }

    /** The learn app is already on the device, and its launch never reports an outcome, so it stays in flight. */
    private fun startLaunchOfInstalledLearnApp(): ConnectJobIntroFragment {
        seedConnectUser()
        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns true
        mockkConstructor(ConnectAppLauncher::class)
        every { anyConstructed<ConnectAppLauncher>().start(any(), any(), any(), any(), any(), any()) } returns Job()
        every { repository.startLearning(any()) } returns flowOf(DataState.Success(Unit))
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        openPage()

        val intro = navHostFragment.opportunityHomeSurface<ConnectJobIntroFragment>()
        val cta = intro.requireView().findViewById<MaterialButton>(R.id.cta_button)
        activity.runOnUiThread { cta.performClick() }
        ShadowLooper.idleMainLooper()

        assertEquals(ConnectJobRecord.STATUS_LEARNING, job.status)
        assertEquals(AppInstallState.Launching, installViewModel().installState.value)
        return intro
    }

    private fun installViewModel(): ConnectAppInstallViewModel =
        ViewModelProvider(
            activity,
            ViewModelProvider.AndroidViewModelFactory.getInstance(activity.application),
        )[ConnectAppInstallViewModel::class.java]
}
