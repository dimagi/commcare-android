package org.commcare.fragments.personalId

import android.location.Location
import androidx.annotation.CallSuper
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.integrity.StandardIntegrityManager
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.android.CommCareViewModelProvider
import org.commcare.android.integrity.IntegrityTokenViewModel
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Before
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

const val TEST_INTEGRITY_TOKEN: String = "test_integrity_token_12345"

/**
 * Base test class for PersonalIdPhoneFragment tests.
 * Contains common setup and teardown logic for fragment testing.
 */
abstract class BasePersonalIdPhoneFragmentTest {
    protected lateinit var mocksCloseable: AutoCloseable
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: PersonalIdPhoneFragment
    protected lateinit var navController: TestNavHostController

    @Mock
    protected lateinit var mockLocation: Location

    @Before
    @CallSuper
    open fun setUp() {
        mocksCloseable = MockitoAnnotations.openMocks(this)
        mockLocation()
        setupMockIntegrityTokenViewModel()
        setUpPersonalIdActivityWithFragment()
    }

    protected fun mockLocation() {
        `when`(mockLocation.latitude).thenReturn(37.7749)
        `when`(mockLocation.longitude).thenReturn(-122.4194)
        `when`(mockLocation.hasAccuracy()).thenReturn(true)
        `when`(mockLocation.accuracy).thenReturn(10.0f)
    }

    protected fun setupMockIntegrityTokenViewModel() {
        val mockToken = mock(StandardIntegrityManager.StandardIntegrityToken::class.java)
        val mockTokenProvider = mock(StandardIntegrityManager.StandardIntegrityTokenProvider::class.java)
        val mockViewModel = mock(IntegrityTokenViewModel::class.java)

        `when`(mockToken.token()).thenReturn(TEST_INTEGRITY_TOKEN)

        // Setup providerState LiveData to return Success
        val providerStateLiveData = MutableLiveData<IntegrityTokenViewModel.TokenProviderState>()
        providerStateLiveData.postValue(IntegrityTokenViewModel.TokenProviderState.Success(mockTokenProvider))
        `when`(mockViewModel.providerState).thenReturn(providerStateLiveData)

        doAnswer { invocation ->
            val callback = invocation.arguments[2] as IntegrityTokenViewModel.IntegrityTokenCallback
            val requestHash = invocation.arguments[0] as String
            callback.onTokenReceived(requestHash, mockToken)
            null
        }.`when`(mockViewModel).requestIntegrityToken(
            any(),
            any(),
            any(),
        )

        // Inject mock into CommCareViewModelProvider using reflection
        val field = CommCareViewModelProvider::class.java.getDeclaredField("integrityTokenViewModel")
        field.isAccessible = true
        field.set(null, mockViewModel)
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

    @After
    @CallSuper
    open fun tearDown() {
        val viewModelField = CommCareViewModelProvider::class.java.getDeclaredField("integrityTokenViewModel")
        viewModelField.isAccessible = true
        viewModelField.set(null, null)

        activityController.pause().stop().destroy()
        mocksCloseable.close()
    }
}
