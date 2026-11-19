package org.commcare.connect

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.commcare.AppUtils
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.AppInstallState
import org.commcare.connect.viewmodel.AppInstallTarget
import org.commcare.connect.viewmodel.ConnectAppInstallViewModel
import org.commcare.dalvik.R
import org.commcare.engine.resource.AppInstallStatus
import org.commcare.fragments.connect.BaseConnectJobIntroTest
import org.commcare.fragments.connect.ConnectDeliveryHomeFragment
import org.commcare.fragments.connect.ConnectJobIntroFragment
import org.commcare.fragments.connect.ConnectLearningProgressFragment
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.personalId.PersonalIdUserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectOpportunityHomeFragmentTest : BaseConnectJobIntroTest() {
    private val repository get() = ConnectRepository.getInstance()

    @Before
    override fun setUp() {
        super.setUp()
        every { repository.getLearningProgress(any(), any(), any()) } returns emptyFlow()
        every { repository.getDeliveryProgress(any(), any(), any()) } returns emptyFlow()
    }

    private fun openPage(args: Bundle? = null): ConnectOpportunityHomeFragment {
        activity.runOnUiThread { navController.navigate(R.id.connect_opportunity_home_fragment, args) }
        ShadowLooper.idleMainLooper()
        return navHostFragment.childFragmentManager.fragments
            .filterIsInstance<ConnectOpportunityHomeFragment>()
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
        assertEquals(R.id.connect_opportunity_home_fragment, navController.currentDestination?.id)
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

    private fun openVisitsDetailAndReturn(page: ConnectOpportunityHomeFragment) {
        activity.runOnUiThread {
            page.navigateFromOpportunityHost(
                ConnectOpportunityHomeFragmentDirections.actionConnectOpportunityHomeFragmentToConnectDeliveryVisitsDetailFragment(
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
            page.navigateFromOpportunityHost(
                ConnectOpportunityHomeFragmentDirections.actionConnectOpportunityHomeFragmentToConnectDeliveryVisitsDetailFragment(
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
        assertEquals(R.id.connect_opportunity_home_fragment, navController.currentDestination?.id)
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
            ConnectOpportunityHomeFragmentDirections.actionConnectOpportunityHomeFragmentToConnectLearnModulesBottomSheet()

        activity.runOnUiThread {
            page.navigateFromOpportunityHost(directions)
            page.navigateFromOpportunityHost(directions)
        }
        ShadowLooper.idleMainLooper()
        assertEquals(R.id.connect_learn_modules_bottom_sheet, navController.currentDestination?.id)

        activity.runOnUiThread { navController.popBackStack() }
        ShadowLooper.idleMainLooper()
        assertEquals(R.id.connect_opportunity_home_fragment, navController.currentDestination?.id)
    }

    @Test
    fun `a lock during an installed app's launch keeps the intro until the launch is over`() {
        val intro = startLaunchOfInstalledLearnApp()

        leaveAndReturnToScreen()

        assertSame(intro, navHostFragment.opportunityHomeSurface<ConnectJobIntroFragment>())
        assertEquals(AppInstallState.Launching, installViewModel().installState.value)
    }

    @Test
    fun `a failed launch moves the page on to the phase the job reached`() {
        startLaunchOfInstalledLearnApp()

        activity.runOnUiThread { installViewModel().clear() }
        ShadowLooper.idleMainLooper()

        navHostFragment.opportunityHomeSurface<ConnectLearningProgressFragment>()
    }

    @Test
    fun `a launch cancelled with the screen's view leaves nothing in flight`() {
        startLaunchOfInstalledLearnApp()

        activity.runOnUiThread { navController.popBackStack() }
        ShadowLooper.idleMainLooper()

        assertEquals(AppInstallState.Idle, installViewModel().installState.value)
    }

    @Test
    fun `a finished download keeps the intro through the hand-off to verification`() {
        val introFragment = startLearnAppDownloadFromIntro()

        activity.runOnUiThread { installViewModel().reportSuccess(true) }
        ShadowLooper.idleMainLooper()

        assertSame(introFragment, navHostFragment.opportunityHomeSurface<ConnectJobIntroFragment>())
        assertEquals(AppInstallState.Verifying, installViewModel().installState.value)
    }

    @Test
    fun `an intro kept on screen by a failed install keeps its close icon`() {
        val introFragment = startLearnAppDownloadFromIntro()

        failAppInstall()

        assertEquals(ConnectJobRecord.STATUS_LEARNING, job.status)
        assertSame(introFragment, navHostFragment.opportunityHomeSurface<ConnectJobIntroFragment>())
        assertEquals(R.drawable.ic_connect_close, shadowOf(getNavigationIcon()).createdFromResId)
        assertEquals(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getDrawerLockMode())
    }

    @Test
    fun `the sidebar is locked while an install runs and unlocked once it fails`() {
        mockkStatic(AppUtils::class)
        every { AppUtils.isAppInstalled(any()) } returns false
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.downloadApp(any(), any()) } returns true
        job.status = ConnectJobRecord.STATUS_LEARNING
        openPageAtTaskBottom()
        val learningProgressFragment = navHostFragment.opportunityHomeSurface<ConnectLearningProgressFragment>()
        val downloadLearnAppButton =
            learningProgressFragment
                .requireView()
                .findViewById<View>(R.id.learnProgressView)
                .findViewById<MaterialButton>(R.id.cta_button)

        activity.runOnUiThread { downloadLearnAppButton.performClick() }
        ShadowLooper.idleMainLooper()
        assertEquals(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getDrawerLockMode())
        assertEquals(0f, (getNavigationIcon() as DrawerArrowDrawable).progress)

        failAppInstall()
        assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, getDrawerLockMode())
        assertEquals(0f, (getNavigationIcon() as DrawerArrowDrawable).progress)
    }

    @Test
    fun `a successful install unlocks the sidebar once the page moves on to learn`() {
        mockkConstructor(ConnectAppLauncher::class)
        every { anyConstructed<ConnectAppLauncher>().start(any(), any(), any(), any(), any(), any()) } answers {
            arg<ConnectAppLauncher.OutcomeCallback>(5).onOutcome(LaunchOutcome.Launched(alreadyLoggedIn = false))
            Job()
        }
        mockkObject(activity.coordinator)
        every { activity.coordinator.enterLaunchedApp(any()) } returns false
        startLearnAppDownloadFromIntro()

        activity.runOnUiThread { installViewModel().reportSuccess(true) }
        ShadowLooper.idleMainLooper()
        assertEquals(AppInstallState.Verifying, installViewModel().installState.value)
        assertEquals(R.drawable.ic_connect_close, shadowOf(getNavigationIcon()).createdFromResId)
        assertEquals(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getDrawerLockMode())

        val verificationIntent = shadowOf(activity).nextStartedActivity
        activity.runOnUiThread { shadowOf(activity).receiveResult(verificationIntent, Activity.RESULT_OK, null) }
        ShadowLooper.idleMainLooper()
        leaveAndReturnToScreen()

        navHostFragment.opportunityHomeSurface<ConnectLearningProgressFragment>()
        assertEquals(AppInstallState.Idle, installViewModel().installState.value)
        assertEquals(0f, (getNavigationIcon() as DrawerArrowDrawable).progress)
        assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, getDrawerLockMode())
    }

    @Test
    fun `an install another screen started does not keep the page from showing a surface`() {
        installViewModel().launch(AppInstallTarget("other-app", true, "other-screen"))
        job.status = ConnectJobRecord.STATUS_LEARNING

        openPage()

        navHostFragment.opportunityHomeSurface<ConnectLearningProgressFragment>()
    }

    @Test
    fun `opening a learning opportunity records it as the last accessed opportunity`() {
        job.status = ConnectJobRecord.STATUS_LEARNING

        openPage()

        assertEquals(job.jobUUID, PersonalIdUserPreferences.getLastAccessedOpportunityUuid())
    }

    @Test
    fun `a phase change out of the intro records the opportunity as last accessed`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        val page = openPage()

        job.status = ConnectJobRecord.STATUS_LEARNING
        activity.runOnUiThread { page.onPhaseChanged() }
        ShadowLooper.idleMainLooper()

        assertEquals(job.jobUUID, PersonalIdUserPreferences.getLastAccessedOpportunityUuid())
    }

    @Test
    fun `opening an opportunity on its intro does not record it as last accessed`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE

        openPage()

        assertNull(PersonalIdUserPreferences.getLastAccessedOpportunityUuid())
    }

    @Test
    fun `opening an ended opportunity marks it seen ended`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        job.setIsActive(false)

        openPage()

        assertTrue(PersonalIdUserPreferences.wasEndedOpportunitySeen(job.jobUUID))
    }

    @Test
    fun `opening a running opportunity clears an earlier seen ended mark`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        PersonalIdUserPreferences.markEndedOpportunitySeen(job.jobUUID)

        openPage()

        assertFalse(PersonalIdUserPreferences.wasEndedOpportunitySeen(job.jobUUID))
    }

    /** The learn app is already on the device, and its launch never reports an outcome, so it stays in flight. */
    private fun startLaunchOfInstalledLearnApp(): ConnectJobIntroFragment {
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

    /** The learn app isn't on the device yet, so tapping Start learning starts its download, which stays in flight. */
    private fun startLearnAppDownloadFromIntro(): ConnectJobIntroFragment {
        mockkObject(ConnectAppUtils)
        every { ConnectAppUtils.downloadApp(any(), any()) } returns true
        every { repository.startLearning(any()) } returns flowOf(DataState.Success(Unit))
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        openPageAtTaskBottom()

        val introFragment = navHostFragment.opportunityHomeSurface<ConnectJobIntroFragment>()
        val startLearningButton = introFragment.requireView().findViewById<MaterialButton>(R.id.cta_button)
        activity.runOnUiThread { startLearningButton.performClick() }
        ShadowLooper.idleMainLooper()
        return introFragment
    }

    private fun leaveAndReturnToScreen() {
        activity.runOnUiThread { activityController.pause().stop() }
        ShadowLooper.idleMainLooper()
        activity.runOnUiThread { activityController.start().resume() }
        ShadowLooper.idleMainLooper()
    }

    private fun installViewModel(): ConnectAppInstallViewModel = ConnectAppInstallViewModel.of(activity)

    private fun openPageAtTaskBottom() {
        activity.runOnUiThread { activity.openOpportunityHomeAtTaskBottom() }
        ShadowLooper.idleMainLooper()
    }

    private fun failAppInstall() {
        activity.runOnUiThread { installViewModel().failUnknown(AppInstallStatus.UnknownFailure) }
        ShadowLooper.idleMainLooper()
    }

    private fun getNavigationIcon(): Drawable? = activity.findViewById<Toolbar>(R.id.toolbar).navigationIcon

    private fun getDrawerLockMode(): Int = activity.findViewById<DrawerLayout>(R.id.drawer_layout).getDrawerLockMode(GravityCompat.START)
}
