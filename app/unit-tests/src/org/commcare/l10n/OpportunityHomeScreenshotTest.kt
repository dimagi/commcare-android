package org.commcare.l10n

import android.content.Intent
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.ConnectActivity
import org.commcare.connect.ConnectConstants
import org.commcare.connect.ConnectOpportunityHomeFragmentDirections
import org.commcare.connect.database.ConnectJobUtils
import org.commcare.connect.opportunityHomeSurface
import org.commcare.connect.repository.ConnectRepository
import org.commcare.connect.repository.ConnectSyncPreferences
import org.commcare.dalvik.R
import org.commcare.fragments.connect.ConnectDeliveryHomeFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@Config(application = CommCareTestApplication::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(ParameterizedRobolectricTestRunner::class)
class OpportunityHomeScreenshotTest(
    target: ScreenshotTarget,
) : BaseConnectScreenshotTest(target) {
    @Before
    fun seedOpportunities() {
        ConnectJobUtils.storeJobs(ApplicationProvider.getApplicationContext(), ConnectScreenshotData.opportunities(), true)
        ConnectSyncPreferences.getInstance().apply {
            markSessionStart()
            storeLastSyncTime(ConnectRepository.SYNC_KEY_OPPORTUNITIES)
        }
    }

    override fun responseBody(path: String): String {
        val opportunityId =
            OPPORTUNITY_PATH
                .find(path)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull() ?: return "{}"
        return when {
            path.endsWith("/learn_progress") -> {
                ConnectScreenshotData.learningProgressResponse(opportunityId)
            }

            path.endsWith("/delivery_progress") && opportunityId == ConnectScreenshotData.DELIVERING_OPPORTUNITY_ID -> {
                ConnectScreenshotData.deliveryProgressResponse()
            }

            else -> {
                "{}"
            }
        }
    }

    @Test
    fun jobIntro() {
        val controller = openOpportunityHome(ConnectScreenshotData.NEW_OPPORTUNITY_ID)
        L10nScreenshots.capture(controller, target, "21_connect_job_intro")
    }

    @Test
    fun learnModulesSheet() {
        val controller = openOpportunityHome(ConnectScreenshotData.NEW_OPPORTUNITY_ID)
        navHostOf(controller).navController.navigate(
            ConnectOpportunityHomeFragmentDirections.actionConnectOpportunityHomeFragmentToConnectLearnModulesBottomSheet(),
        )
        ShadowLooper.idleMainLooper()
        L10nScreenshots.captureScreen(controller, target, "22_connect_learn_modules_sheet")
    }

    @Test
    fun learningProgress() {
        val controller = openOpportunityHome(ConnectScreenshotData.LEARNING_OPPORTUNITY_ID)
        mockApi.awaitRequest()
        L10nScreenshots.capture(controller, target, "23_connect_learning_progress")
    }

    @Test
    fun deliveryDashboard() = captureDeliveryTab(ConnectDeliveryHomeFragment.TAB_DASHBOARD, "24_connect_delivery_dashboard")

    @Test
    fun deliveryPayment() = captureDeliveryTab(ConnectDeliveryHomeFragment.TAB_PAYMENT, "25_connect_delivery_payment")

    @Test
    fun deliveryVisits() = captureDeliveryTab(ConnectDeliveryHomeFragment.TAB_VISITS, "26_connect_delivery_visits")

    @Test
    fun deliveryMore() = captureDeliveryTab(ConnectDeliveryHomeFragment.TAB_MORE, "27_connect_delivery_more")

    /** Delivery progress syncs first, and its success fetches the learn records the More tab reads. */
    private fun captureDeliveryTab(
        tab: Int,
        screen: String,
    ) {
        val controller = openOpportunityHome(ConnectScreenshotData.DELIVERING_OPPORTUNITY_ID)
        mockApi.awaitRequest()
        mockApi.awaitRequest()
        navHostOf(controller)
            .opportunityHomeSurface<ConnectDeliveryHomeFragment>()
            .requireView()
            .findViewById<ViewPager2>(R.id.connect_delivery_home_view_pager)
            .setCurrentItem(tab, false)
        ShadowLooper.idleMainLooper()
        L10nScreenshots.capture(controller, target, screen)
    }

    private fun openOpportunityHome(opportunityId: Int): ActivityController<ConnectActivity> {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), ConnectActivity::class.java).apply {
                putExtra(ConnectConstants.GO_TO_JOB_STATUS, true)
                putExtra(ConnectConstants.OPPORTUNITY_UUID, ConnectScreenshotData.opportunityUuid(opportunityId))
            }
        val controller =
            Robolectric
                .buildActivity(ConnectActivity::class.java, intent)
                .create()
                .postCreate(null)
                .start()
                .resume()
        ShadowLooper.idleMainLooper()
        return controller
    }

    private fun navHostOf(controller: ActivityController<ConnectActivity>): NavHostFragment =
        controller.get().supportFragmentManager.findFragmentById(R.id.nav_host_fragment_connect) as NavHostFragment

    companion object {
        private val OPPORTUNITY_PATH = Regex("/opportunity/showcase-opportunity-(\\d+)/")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun locales() = L10nScreenshots.parameters()
    }
}
