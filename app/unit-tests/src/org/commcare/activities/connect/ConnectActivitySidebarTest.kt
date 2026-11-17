package org.commcare.activities.connect

import android.os.Build
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.emptyFlow
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.util.ActivityAssertions.assertStarted
import org.commcare.android.util.ActivityAssertions.assertStartedNothing
import org.commcare.connect.ConnectActivityCompleteListener
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ConnectOpportunityHomeFragment
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.repository.ConnectRepository
import org.commcare.dalvik.R
import org.commcare.fragments.connect.BaseConnectJobIntroTest
import org.commcare.personalId.PersonalIdUnlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class, sdk = [Build.VERSION_CODES.Q])
@RunWith(AndroidJUnit4::class)
class ConnectActivitySidebarTest : BaseConnectJobIntroTest() {
    @Before
    override fun setUp() {
        super.setUp()
        activityController.visible()
        val repository = ConnectRepository.getInstance()
        every { repository.getLearningProgress(any(), any(), any()) } returns emptyFlow()
        every { repository.getDeliveryProgress(any(), any(), any()) } returns emptyFlow()
        mockkObject(PersonalIdUnlocker)
        every { PersonalIdUnlocker.unlock(any(), any(), any()) } answers {
            thirdArg<ConnectActivityCompleteListener>().connectActivityComplete(true, null)
        }
    }

    @Test
    fun `the opportunity list shows the sidebar`() {
        assertEquals(R.id.connect_jobs_list_fragment, navController.currentDestination?.id)
        assertNotNull(activity.findViewById<RecyclerView>(R.id.nav_drawer_recycler).adapter)
        assertShowsSidebar()
    }

    @Test
    fun `opportunity home with nothing beneath it shows the sidebar`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING

        openOpportunityHomeAtTaskBottom()

        assertShowsSidebar()
    }

    @Test
    fun `the intro opened from the list keeps its close icon and locks the sidebar`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE

        navigateToIntroFragment()

        assertShowsIntroCloseIcon()
    }

    @Test
    fun `leaving the intro with nothing beneath it shows the sidebar`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        val opportunityHome = openOpportunityHomeAtTaskBottom()
        assertShowsIntroCloseIcon()

        job.status = ConnectJobRecord.STATUS_LEARNING
        activity.runOnUiThread { opportunityHome.onPhaseChanged() }
        ShadowLooper.idleMainLooper()

        assertShowsSidebar()
    }

    @Test
    fun `the intro with nothing beneath it keeps its close icon after the screen is recreated`() {
        job.status = ConnectJobRecord.STATUS_AVAILABLE
        openOpportunityHomeAtTaskBottom()

        recreateActivity()

        assertShowsIntroCloseIcon()
    }

    @Test
    fun `a screen opened from opportunity home shows back and locks the sidebar`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        openOpportunityHomeAtTaskBottom()

        activity.runOnUiThread {
            navController.navigate(R.id.connect_delivery_visits_detail_fragment, bundleOf("unitId" to "unit-1"))
        }
        ShadowLooper.idleMainLooper()

        assertEquals(1f, (getNavigationIcon() as DrawerArrowDrawable).progress)
        assertEquals(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getDrawerLockMode())
    }

    @Test
    fun `back from a screen opened from opportunity home returns to it with the sidebar`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        openOpportunityHomeAtTaskBottom()
        activity.runOnUiThread {
            navController.navigate(R.id.connect_delivery_visits_detail_fragment, bundleOf("unitId" to "unit-1"))
        }
        ShadowLooper.idleMainLooper()

        getNavigationButton().performClick()
        ShadowLooper.idleMainLooper()

        assertEquals(R.id.connect_opportunity_home_fragment, navController.currentDestination?.id)
        assertShowsSidebar()
    }

    @Test
    fun `tapping opportunities on the list only closes the sidebar`() {
        val drawerLayout = activity.findViewById<DrawerLayout>(R.id.drawer_layout)
        drawerLayout.openDrawer(GravityCompat.START, false)
        var drawerStartedClosing = false
        drawerLayout.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerStateChanged(newState: Int) {
                    if (newState == DrawerLayout.STATE_SETTLING) {
                        drawerStartedClosing = true
                    }
                }
            },
        )

        clickDrawerItem(R.string.nav_drawer_opportunities)

        assertTrue(drawerStartedClosing)
        assertStartedNothing(activity)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `opening messaging from a list opened as a section replaces the list`() {
        shadowOf(activity).setIsTaskRoot(false)

        clickDrawerItem(R.string.connect_messaging_title)

        assertStarted(activity, ConnectMessagingActivity::class.java)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `opening messaging from the list the app opened on keeps the list`() {
        clickDrawerItem(R.string.connect_messaging_title)

        assertStarted(activity, ConnectMessagingActivity::class.java)
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `opening opportunities from opportunity home keeps opportunity home beneath the list`() {
        job.status = ConnectJobRecord.STATUS_DELIVERING
        openOpportunityHomeAtTaskBottom()

        clickDrawerItem(R.string.nav_drawer_opportunities)

        val opportunityListIntent = assertStarted(activity, ConnectActivity::class.java)
        assertFalse(opportunityListIntent.hasExtra(ConnectConstants.GO_TO_JOB_STATUS))
        assertFalse(opportunityListIntent.hasExtra(ConnectConstants.REDIRECT_ACTION))
        assertFalse(activity.isFinishing)
    }

    private fun openOpportunityHomeAtTaskBottom(): ConnectOpportunityHomeFragment {
        activity.runOnUiThread { activity.openOpportunityHomeAtTaskBottom() }
        ShadowLooper.idleMainLooper()
        return navHostFragment.childFragmentManager.fragments
            .filterIsInstance<ConnectOpportunityHomeFragment>()
            .single()
    }

    private fun recreateActivity() {
        mockkStatic(ConnectJobUtils::class)
        every { ConnectJobUtils.getCompositeJob(job.jobUUID) } returns job
        activityController.recreate()
        activity = activityController.get()
        navHostFragment = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect) as NavHostFragment
        ShadowLooper.idleMainLooper()
    }

    private fun assertShowsSidebar() {
        assertEquals(0f, (getNavigationIcon() as DrawerArrowDrawable).progress)
        assertEquals(DrawerLayout.LOCK_MODE_UNLOCKED, getDrawerLockMode())
    }

    private fun assertShowsIntroCloseIcon() {
        assertEquals(R.drawable.ic_connect_close, shadowOf(getNavigationIcon()).createdFromResId)
        assertEquals(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, getDrawerLockMode())
    }

    private fun getNavigationIcon() = activity.findViewById<Toolbar>(R.id.toolbar).navigationIcon

    private fun getNavigationButton(): ImageButton {
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        return toolbar.children.filterIsInstance<ImageButton>().first { it.drawable === toolbar.navigationIcon }
    }

    private fun getDrawerLockMode(): Int = activity.findViewById<DrawerLayout>(R.id.drawer_layout).getDrawerLockMode(GravityCompat.START)

    private fun clickDrawerItem(titleRes: Int) {
        val title = activity.getString(titleRes)
        val recycler = activity.findViewById<RecyclerView>(R.id.nav_drawer_recycler)
        val item =
            recycler.children.firstOrNull { it.findViewById<TextView>(R.id.list_title).text == title }
                ?: throw AssertionError("no sidebar item titled '$title'")
        item.performClick()
        ShadowLooper.idleMainLooper()
    }
}
