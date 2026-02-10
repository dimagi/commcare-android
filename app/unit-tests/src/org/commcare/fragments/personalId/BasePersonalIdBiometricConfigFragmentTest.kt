package org.commcare.fragments.personalId

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.dalvik.R
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.junit.After
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdBiometricConfigFragment tests.
 * Contains common setup and teardown logic for fragment testing.
 */
abstract class BasePersonalIdBiometricConfigFragmentTest {
    protected lateinit var mocksCloseable: AutoCloseable
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: PersonalIdBiometricConfigFragment
    protected lateinit var navController: TestNavHostController

    @Mock
    protected lateinit var mockBiometricManager: BiometricManager

    @Before
    open fun setUp() {
        mocksCloseable = MockitoAnnotations.openMocks(this)
        MockAndroidKeyStoreProvider.registerProvider()
    }

    protected fun setUpBiometricFragment(requiredLock: String = PersonalIdSessionData.PIN) {
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        // Set up ViewModel with initial data before fragment navigation
        val sessionData =
            PersonalIdSessionData(
                requiredLock = requiredLock,
                demoUser = false,
            )

        // Get the ViewModel from the activity and populate it with test data
        activity.runOnUiThread {
            val viewModel = ViewModelProvider(activity)[PersonalIdSessionDataViewModel::class.java]
            viewModel.setPersonalIdSessionData(sessionData)
        }
        ShadowLooper.idleMainLooper()

        val navHostFragment =
            activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_connectid) as NavHostFragment

        // Set up TestNavController and attach it to the NavHostFragment view BEFORE creating the fragment
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.setGraph(R.navigation.nav_graph_personalid)
        navController.setCurrentDestination(R.id.personalid_biometric_config)

        activity.runOnUiThread {
            Navigation.setViewNavController(navHostFragment.requireView(), navController)
            val testableFragment = TestablePersonalIdBiometricConfigFragment(mockBiometricManager, navController)
            navHostFragment.childFragmentManager
                .beginTransaction()
                .replace(R.id.nav_host_fragment_connectid, testableFragment)
                .commitNow()
            fragment = testableFragment
        }

        ShadowLooper.idleMainLooper()
    }

    @After
    open fun tearDown() {
        activityController.pause().stop().destroy()
        mocksCloseable.close()
        MockAndroidKeyStoreProvider.deregisterProvider()
    }
}

/**
 * Testable subclass of PersonalIdBiometricConfigFragment that allows injecting a mock BiometricManager
 * and a test NavController for navigation testing.
 */
class TestablePersonalIdBiometricConfigFragment(
    private val mockBiometricManager: BiometricManager,
    private val testNavController: NavController? = null,
) : PersonalIdBiometricConfigFragment() {
    override fun getBiometricManager(): BiometricManager = mockBiometricManager

    override fun getNavController(): NavController = testNavController ?: super.getNavController()

    /**
     * Simulates a successful biometric authentication by directly triggering the authentication callback.
     */
    fun simulateSuccessfulAuthentication() {
        val callbackField = PersonalIdBiometricConfigFragment::class.java.getDeclaredField("biometricCallback")
        callbackField.isAccessible = true
        val callback = callbackField.get(this) as BiometricPrompt.AuthenticationCallback
        val result = org.mockito.Mockito.mock(BiometricPrompt.AuthenticationResult::class.java)
        callback.onAuthenticationSucceeded(result)
    }
}
