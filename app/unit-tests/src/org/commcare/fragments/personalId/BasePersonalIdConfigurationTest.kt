package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.integrity.StandardIntegrityManager
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.CommCareViewModelProvider
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.integrity.IntegrityTokenViewModel
import org.commcare.dalvik.R
import org.junit.After
import org.junit.Before
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

const val TEST_INTEGRITY_TOKEN: String = "test_integrity_token_12345"

/**
 * Shared base for PersonalID-flow fragment tests that boot [org.commcare.activities.connect.PersonalIdActivity].
 *
 * The nav graph's start destination is the phone fragment, which constructs an
 * `IntegrityTokenApiRequestHelper` in `onCreateView` — that in turn instantiates a real
 * [IntegrityTokenViewModel] whose `init` block requires a non-default `GOOGLE_CLOUD_PROJECT_NUMBER`
 * BuildConfig value and therefore fails under Robolectric. This base injects a mocked
 * viewmodel via [CommCareViewModelProvider] reflection before any activity setup runs, and
 * clears the static field again in teardown so tests do not leak into each other.
 *
 * Concrete fragment-specific bases (e.g. [BasePersonalIdPhoneFragmentTest],
 * [BasePersonalIdEmailFragmentTest]) extend this and add their own fragment / activity setup,
 * calling `super.setUp()` and `super.tearDown()` so the integrity mock is in place at the
 * moment `PersonalIdActivity` boots.
 */
abstract class BasePersonalIdConfigurationTest<T : BasePersonalIdFragment> {
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var navHostFragment: NavHostFragment
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: T
    protected lateinit var navController: TestNavHostController

    @Before
    @CallSuper
    open fun setUp() {
        setupMockIntegrityTokenViewModel()
    }

    @After
    @CallSuper
    open fun tearDown() {
        val viewModelField = CommCareViewModelProvider::class.java.getDeclaredField("integrityTokenViewModel")
        viewModelField.isAccessible = true
        viewModelField.set(null, null)
    }

    protected fun bootActivity() {
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment
    }

    protected fun bootActivityAndSeedSession(sessionData: PersonalIdSessionData) {
        bootActivity()
        activity.runOnUiThread {
            ViewModelProvider(activity)[PersonalIdSessionDataViewModel::class.java]
                .personalIdSessionData = sessionData
        }
        ShadowLooper.idleMainLooper()
    }

    protected fun launchFragmentForTest(
        sessionData: PersonalIdSessionData,
        @IdRes destinationId: Int,
        createFragment: (NavController) -> T,
    ) {
        bootActivityAndSeedSession(sessionData)

        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.setGraph(R.navigation.nav_graph_personalid)
        navController.setCurrentDestination(destinationId)

        activity.runOnUiThread {
            Navigation.setViewNavController(navHostFragment.requireView(), navController)
            fragment = createFragment(navController)
            navHostFragment.childFragmentManager
                .beginTransaction()
                .replace(R.id.nav_host_fragment_connectid, fragment)
                .commitNow()
        }
        ShadowLooper.idleMainLooper()
    }

    protected fun navigateToFragment(
        sessionData: PersonalIdSessionData,
        @IdRes destinationId: Int,
        args: Bundle? = null,
    ) {
        bootActivityAndSeedSession(sessionData)
        activity.runOnUiThread {
            navHostFragment.navController.navigate(destinationId, args)
        }
        ShadowLooper.idleMainLooper()
        captureNavFragment()
    }

    @Suppress("UNCHECKED_CAST")
    protected fun captureNavFragment() {
        fragment =
            navHostFragment.childFragmentManager
                .primaryNavigationFragment as T
    }

    /**
     * Builds a [TestNavHostController] parked at [destinationId] (with optional [args]) and attaches
     * it to [view] so navigation can be driven and observed in tests. Stores it in [navController].
     * Must be called on the UI thread, after the hosting view exists.
     */
    protected fun installTestNavController(
        view: View,
        @IdRes destinationId: Int,
        args: Bundle? = null,
    ) {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.setGraph(R.navigation.nav_graph_personalid)
        navController.setCurrentDestination(destinationId, args ?: Bundle())
        Navigation.setViewNavController(view, navController)
    }

    private fun setupMockIntegrityTokenViewModel() {
        val mockToken = mock(StandardIntegrityManager.StandardIntegrityToken::class.java)
        val mockTokenProvider = mock(StandardIntegrityManager.StandardIntegrityTokenProvider::class.java)
        val mockViewModel = mock(IntegrityTokenViewModel::class.java)

        `when`(mockToken.token()).thenReturn(TEST_INTEGRITY_TOKEN)

        val providerStateLiveData = MutableLiveData<IntegrityTokenViewModel.TokenProviderState>()
        providerStateLiveData.postValue(
            IntegrityTokenViewModel.TokenProviderState.Success(mockTokenProvider),
        )
        `when`(mockViewModel.providerState).thenReturn(providerStateLiveData)

        doAnswer { invocation ->
            val callback = invocation.arguments[2] as IntegrityTokenViewModel.IntegrityTokenCallback
            val requestHash = invocation.arguments[0] as String
            callback.onTokenReceived(requestHash, mockToken)
            null
        }.`when`(mockViewModel).requestIntegrityToken(any(), any(), any())

        val field = CommCareViewModelProvider::class.java.getDeclaredField("integrityTokenViewModel")
        field.isAccessible = true
        field.set(null, mockViewModel)
    }
}
