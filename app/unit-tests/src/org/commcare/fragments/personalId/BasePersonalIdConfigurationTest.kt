package org.commcare.fragments.personalId

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.CommCareViewModelProvider
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.integrity.IntegrityTokenViewModel
import org.commcare.connect.network.ApiService
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.connectId.PersonalIdApiClient
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
import java.util.concurrent.TimeUnit

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

    // Only initialized for tests that opt in via setupMockWebServer()
    protected lateinit var mockWebServer: MockWebServer
    private lateinit var httpDispatcher: Dispatcher

    // Used to avoid late callbacks to the main looper (i.e. an unanswered call failing on server shutdown)
    // They would leak into the next test and run against a destroyed fragment.
    @Volatile
    private var dispatchHttpCallbacks = true

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

    /**
     * Starts a [MockWebServer] and points [PersonalIdApiClient] at it so PersonalID API calls hit
     * the mock server. Tests that call this from `setUp` must call [tearDownMockWebServer] from
     * `tearDown`.
     *
     * On a device Retrofit posts callbacks to the main thread, but on the JVM it invokes them
     * directly on the OkHttp dispatcher thread, where Robolectric view access is unsafe and
     * thread-local Mockito static mocks do not intercept. An explicit main-looper
     * `callbackExecutor` restores production threading; [drainHttp] then runs the callback on the
     * test thread deterministically.
     */
    protected fun setupMockWebServer() {
        dispatchHttpCallbacks = true
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit =
            BaseApiClient
                .buildRetrofitClient(mockWebServer.url("/").toString(), PersonalIdApiClient.API_VERSION)
                .newBuilder()
                .callbackExecutor {
                    if (dispatchHttpCallbacks) {
                        Handler(Looper.getMainLooper()).post(it)
                    }
                }.build()
        httpDispatcher = (retrofit.callFactory() as OkHttpClient).dispatcher
        setPersonalIdApiService(retrofit.create(ApiService::class.java))
    }

    protected fun tearDownMockWebServer() {
        dispatchHttpCallbacks = false
        setPersonalIdApiService(null)
        mockWebServer.shutdown()
    }

    private fun setPersonalIdApiService(apiService: ApiService?) {
        val apiServiceField = PersonalIdApiClient::class.java.getDeclaredField("apiService")
        apiServiceField.isAccessible = true
        apiServiceField.set(null, apiService)
    }

    /**
     * Reads the next request with a bounded wait so a missing dispatch fails fast instead of
     * hanging the suite.
     */
    protected fun takeRequestOrFail(timeoutSeconds: Long = 5): RecordedRequest =
        mockWebServer.takeRequest(timeoutSeconds, TimeUnit.SECONDS)
            ?: throw AssertionError("Expected an HTTP request within ${timeoutSeconds}s but none arrived")

    /**
     * Waits for the next request to reach the mock server and its response callback to be posted
     * to the main looper, then drains UI work so the callback runs before assertions.
     */
    protected fun drainHttp() {
        takeRequestOrFail()
        awaitHttpCallbackPosted()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    /**
     * Blocks until the OkHttp dispatcher finishes the in-flight call, which (because of the
     * main-looper callback executor) means the Retrofit callback has been posted and a subsequent
     * looper drain is guaranteed to run it.
     */
    private fun awaitHttpCallbackPosted(timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (httpDispatcher.runningCallsCount() > 0) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("HTTP call did not complete within ${timeoutMs}ms")
            }
            Thread.sleep(10)
        }
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
