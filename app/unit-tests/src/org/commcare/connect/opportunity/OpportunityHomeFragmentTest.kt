package org.commcare.connect.opportunity

import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.dalvik.R
import org.commcare.fragments.connect.BaseConnectJobIntroTest
import org.commcare.fragments.connect.ConnectDeliveryHomeFragment
import org.commcare.fragments.connect.ConnectJobIntroFragment
import org.commcare.fragments.connect.ConnectLearningProgressFragment
import org.junit.Assert.assertEquals
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
}
