package org.commcare.fragments.connect

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.emptyFlow
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.MessageManager
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.repository.ConnectRepository
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper

/**
 * Boots a real [ConnectActivity] so [ConnectJobFragment] can read the active job from it, and
 * seeds a deterministic [ConnectJobRecord] built from JSON. Concrete tests drive real views
 * (clicks, rendered text) rather than calling fragment methods directly.
 */
abstract class BaseConnectJobIntroTest {
    protected lateinit var activity: ConnectActivity
    protected lateinit var navHostFragment: NavHostFragment
    protected val navController: NavController get() = navHostFragment.navController
    protected lateinit var job: ConnectJobRecord

    private lateinit var savedStatus: PersonalIdManager.PersonalIdStatus

    @Before
    open fun setUp() {
        savedStatus = PersonalIdManager.getInstance().status
        PersonalIdManager.getInstance().status = PersonalIdManager.PersonalIdStatus.LoggedIn

        mockkStatic(MessageManager::class)
        every { MessageManager.retrieveMessages(any(), any()) } returns Unit

        // The jobs-list start destination fetches opportunities on boot; stub the repository so no
        // real network call is made (which crashes background coroutines under Robolectric).
        mockkObject(ConnectRepository.Companion)
        val repository = mockk<ConnectRepository>(relaxed = true)
        every { ConnectRepository.getInstance(any()) } returns repository
        every { repository.getOpportunities(any(), any()) } returns emptyFlow()

        mockkStatic(FirebaseAnalyticsUtil::class)
        every { FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener() } returns
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) = Unit
            }
        every { FirebaseAnalyticsUtil.reportCccApiStartLearning(any()) } returns Unit

        activity =
            Robolectric
                .buildActivity(ConnectActivity::class.java)
                .create()
                .start()
                .resume()
                .get()

        navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connect) as NavHostFragment

        job = buildJob()
        activity.setActiveJob(job)
    }

    @After
    open fun tearDown() {
        PersonalIdManager.getInstance().status = savedStatus
        unmockkAll()
    }

    /**
     * Drives the real nav controller from the jobs-list start destination to the intro fragment
     * (which reads the seeded active job) and returns the hosted fragment instance.
     */
    protected fun navigateToIntroFragment(): ConnectJobIntroFragment {
        activity.runOnUiThread {
            navController.navigate(
                R.id.action_connect_jobs_list_fragment_to_connect_job_intro_fragment,
            )
        }
        ShadowLooper.idleMainLooper()
        return navHostFragment.childFragmentManager.primaryNavigationFragment as ConnectJobIntroFragment
    }

    protected fun showBottomSheet(fragment: androidx.fragment.app.DialogFragment) {
        activity.runOnUiThread {
            fragment.show(activity.supportFragmentManager, "sheet")
        }
        ShadowLooper.idleMainLooper()
        activity.supportFragmentManager.executePendingTransactions()
        ShadowLooper.idleMainLooper()
    }

    private fun buildJob(): ConnectJobRecord =
        ConnectJobRecord.fromJson(
            JSONObject().apply {
                put("id", 1)
                put("opportunity_id", "job-uuid-1")
                put("name", "Infant Vaccination")
                put("description", "One line description about the opportunity.")
                put("organization", "Test Org")
                put("end_date", "2027-12-31")
                put("start_date", "2025-01-01")
                put("max_visits_per_user", 100)
                put("daily_max_visits_per_user", 10)
                put("budget_per_visit", 25)
                put("budget_per_user", 2500)
                put("currency", "INR")
                put("short_description", "Short description")
                put("deliver_progress", 0)
                put("payment_units", paymentUnitsJson())
                put(
                    "learn_progress",
                    JSONObject().apply {
                        put("total_modules", 2)
                        put("completed_modules", 0)
                    },
                )
                put("learn_app", appJson(withModules = true))
                put("deliver_app", appJson(withModules = false))
            },
        )

    private fun paymentUnitsJson(): JSONArray =
        JSONArray().apply {
            put(paymentUnitJson(1, "Registration"))
            put(paymentUnitJson(2, "Follow-up"))
        }

    private fun paymentUnitJson(
        id: Int,
        name: String,
    ): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("payment_unit_id", "unit-$id")
            put("name", name)
            put("max_total", 50)
            put("max_daily", 5)
            put("amount", 25)
        }

    private fun appJson(withModules: Boolean): JSONObject =
        JSONObject().apply {
            put("cc_domain", "test-domain")
            put("cc_app_id", "app-id-001")
            put("name", "Test App")
            put("description", "Test app description")
            put("organization", "Test Org")
            put("passing_score", 80)
            put("install_url", "https://example.com/install")
            put(
                "learn_modules",
                if (withModules) modulesJson() else JSONArray(),
            )
        }

    private fun modulesJson(): JSONArray =
        JSONArray().apply {
            put(moduleJson("Infant Vaccination", 1))
            put(moduleJson("Barriers to Vaccination", 1))
        }

    private fun moduleJson(
        name: String,
        hours: Int,
    ): JSONObject =
        JSONObject().apply {
            put("slug", name.lowercase().replace(' ', '-'))
            put("name", name)
            put("description", "Description of $name")
            put("time_estimate", hours)
        }
}
