package org.commcare.fragments.personalId

import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Before
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdNameFragment tests.
 * Sets up a PersonalIdActivity, populates the session ViewModel, and replaces the
 * default start fragment with PersonalIdNameFragment under a TestNavHostController.
 */
abstract class BasePersonalIdNameFragmentTest {
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: PersonalIdNameFragment
    protected lateinit var navController: TestNavHostController

    protected open fun buildSessionData(): PersonalIdSessionData =
        PersonalIdSessionData(
            requiredLock = PersonalIdSessionData.PIN,
            demoUser = false,
            token = TEST_SESSION_TOKEN,
        )

    @Before
    open fun setUp() {
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        // Seed the session ViewModel before swapping in the name fragment so onCreateView reads it.
        val sessionData = buildSessionData()
        activity.runOnUiThread {
            val viewModel = ViewModelProvider(activity)[PersonalIdSessionDataViewModel::class.java]
            viewModel.personalIdSessionData = sessionData
        }
        ShadowLooper.idleMainLooper()

        val navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment

        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.setGraph(R.navigation.nav_graph_personalid)
        navController.setCurrentDestination(R.id.personalid_name)

        activity.runOnUiThread {
            Navigation.setViewNavController(navHostFragment.requireView(), navController)
            val nameFragment = PersonalIdNameFragment()
            navHostFragment.childFragmentManager
                .beginTransaction()
                .replace(R.id.nav_host_fragment_connectid, nameFragment)
                .commitNow()
            fragment = nameFragment
        }

        ShadowLooper.idleMainLooper()
    }

    @After
    open fun tearDown() {
        activityController.pause().stop().destroy()
    }

    companion object {
        const val TEST_SESSION_TOKEN: String = "test_session_token_abc"
    }
}
