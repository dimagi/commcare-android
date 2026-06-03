package org.commcare.fragments.personalId

import android.location.Location
import androidx.annotation.CallSuper
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.dalvik.R
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdPhoneFragment tests.
 * Inherits integrity-token mock setup from [BasePersonalIdConfigurationTest] and adds
 * phone-fragment-specific setup (Mockito annotations, mocked Location, activity host).
 */
abstract class BasePersonalIdPhoneFragmentTest : BasePersonalIdConfigurationTest() {
    protected lateinit var mocksCloseable: AutoCloseable
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: PersonalIdPhoneFragment
    protected lateinit var navController: TestNavHostController

    @Mock
    protected lateinit var mockLocation: Location

    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        mocksCloseable = MockitoAnnotations.openMocks(this)
        mockLocation()
        setUpPersonalIdActivityWithFragment()
    }

    protected fun mockLocation() {
        `when`(mockLocation.latitude).thenReturn(37.7749)
        `when`(mockLocation.longitude).thenReturn(-122.4194)
        `when`(mockLocation.hasAccuracy()).thenReturn(true)
        `when`(mockLocation.accuracy).thenReturn(10.0f)
    }

    protected fun setUpPersonalIdActivityWithFragment() {
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        val navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment
        fragment =
            navHostFragment.childFragmentManager
                .primaryNavigationFragment as PersonalIdPhoneFragment

        // Set up TestNavHostController for testing navigation
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        activity.runOnUiThread {
            navController.setGraph(R.navigation.nav_graph_personalid)
            navController.setCurrentDestination(R.id.personalid_phone_fragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }
        ShadowLooper.idleMainLooper()
    }

    @CallSuper
    override fun tearDown() {
        activityController.pause().stop().destroy()
        mocksCloseable.close()
        super.tearDown()
    }
}
