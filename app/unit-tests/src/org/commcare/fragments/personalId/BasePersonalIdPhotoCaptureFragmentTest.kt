package org.commcare.fragments.personalId

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import org.commcare.activities.connect.PersonalIdActivity
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ApiPersonalId
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MediaUtil
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.junit.After
import org.junit.Before
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdPhotoCaptureFragment tests.
 * Owns setup/teardown of the activity, the fragment, the test NavController,
 * and the static mocks that prevent the fragment from hitting the network,
 * database, analytics, and bitmap decoding during tests.
 */
abstract class BasePersonalIdPhotoCaptureFragmentTest {
    protected lateinit var mocksCloseable: AutoCloseable
    protected lateinit var activityController: ActivityController<PersonalIdActivity>
    protected lateinit var activity: PersonalIdActivity
    protected lateinit var fragment: TestablePersonalIdPhotoCaptureFragment
    protected lateinit var navController: TestNavHostController

    protected lateinit var personalIdManagerMock: MockedStatic<PersonalIdManager>
    protected lateinit var connectDatabaseHelperMock: MockedStatic<ConnectDatabaseHelper>
    protected lateinit var connectUserDatabaseUtilMock: MockedStatic<ConnectUserDatabaseUtil>
    protected lateinit var firebaseAnalyticsUtilMock: MockedStatic<FirebaseAnalyticsUtil>
    protected lateinit var mediaUtilMock: MockedStatic<MediaUtil>
    protected lateinit var apiPersonalIdMock: MockedStatic<ApiPersonalId>
    protected lateinit var errorHandlerMock: MockedStatic<PersonalIdOrConnectApiErrorHandler>

    @Mock
    protected lateinit var mockPersonalIdManager: PersonalIdManager

    @Mock
    protected lateinit var mockBitmap: Bitmap

    protected val testSessionData =
        PersonalIdSessionData(
            requiredLock = PersonalIdSessionData.PIN,
            demoUser = false,
            token = "test-token",
            personalId = "test-personal-id",
            dbKey = "test-db-key",
            oauthPassword = "test-oauth-pwd",
            userName = "Test User",
            phoneNumber = "+11234567890",
            backupCode = "123456",
            invitedUser = false,
        )

    @Before
    open fun setUp() {
        mocksCloseable = MockitoAnnotations.openMocks(this)
        MockAndroidKeyStoreProvider.registerProvider()
        openStaticMocks()
        setUpPhotoCaptureFragment()
    }

    private fun openStaticMocks() {
        personalIdManagerMock = Mockito.mockStatic(PersonalIdManager::class.java)
        personalIdManagerMock
            .`when`<PersonalIdManager> { PersonalIdManager.getInstance() }
            .thenReturn(mockPersonalIdManager)

        connectDatabaseHelperMock = Mockito.mockStatic(ConnectDatabaseHelper::class.java)
        connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
        firebaseAnalyticsUtilMock = Mockito.mockStatic(FirebaseAnalyticsUtil::class.java)
        firebaseAnalyticsUtilMock
            .`when`<NavController.OnDestinationChangedListener> {
                FirebaseAnalyticsUtil.getNavControllerPageChangeLoggingListener()
            }.thenReturn(
                NavController.OnDestinationChangedListener { _: NavController, _: NavDestination, _: android.os.Bundle? ->
                },
            )
        mediaUtilMock = Mockito.mockStatic(MediaUtil::class.java)
        mediaUtilMock
            .`when`<Bitmap> { MediaUtil.decodeBase64EncodedBitmap(any()) }
            .thenReturn(mockBitmap)

        apiPersonalIdMock = Mockito.mockStatic(ApiPersonalId::class.java)

        errorHandlerMock = Mockito.mockStatic(PersonalIdOrConnectApiErrorHandler::class.java)
        errorHandlerMock
            .`when`<String> {
                PersonalIdOrConnectApiErrorHandler.handle(
                    any(),
                    any(),
                    Mockito.nullable(Throwable::class.java),
                )
            }.thenReturn("Network error occurred")
    }

    protected fun setUpPhotoCaptureFragment(sessionData: PersonalIdSessionData = testSessionData) {
        activityController = Robolectric.buildActivity(PersonalIdActivity::class.java)
        activity =
            activityController
                .create()
                .start()
                .resume()
                .get()

        // Seed the ViewModel before swapping the fragment in; onCreateView reads it.
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
        navController.setCurrentDestination(R.id.personalid_photo_capture)

        activity.runOnUiThread {
            Navigation.setViewNavController(navHostFragment.requireView(), navController)
            val testableFragment = TestablePersonalIdPhotoCaptureFragment(navController)
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
        val errors = mutableListOf<Throwable>()
        listOf(
            { activityController.pause().stop().destroy() },
            { errorHandlerMock.close() },
            { apiPersonalIdMock.close() },
            { mediaUtilMock.close() },
            { firebaseAnalyticsUtilMock.close() },
            { connectUserDatabaseUtilMock.close() },
            { connectDatabaseHelperMock.close() },
            { personalIdManagerMock.close() },
            { mocksCloseable.close() },
            { MockAndroidKeyStoreProvider.deregisterProvider() },
        ).forEach { step ->
            try {
                step()
            } catch (t: Throwable) {
                errors.add(t)
            }
        }
        if (errors.isNotEmpty()) {
            val first = errors.first()
            errors.drop(1).forEach { first.addSuppressed(it) }
            throw first
        }
    }
}

/**
 * Testable subclass of PersonalIdPhotoCaptureFragment that exposes:
 *  - an injectable NavController (matches PersonalIdBiometricConfigFragment pattern)
 *  - reflection helpers to drive the private callback methods
 *  - a helper to replace the takePhotoLauncher with a mock for intent-capture tests
 *  - a setter for the private photoAsBase64 field
 */
class TestablePersonalIdPhotoCaptureFragment(
    private val testNavController: NavController? = null,
) : PersonalIdPhotoCaptureFragment() {
    override fun getNavController(): NavController = testNavController ?: super.getNavController()

    fun replaceTakePhotoLauncher(launcher: ActivityResultLauncher<Intent>) {
        val field = PersonalIdPhotoCaptureFragment::class.java.getDeclaredField("takePhotoLauncher")
        field.isAccessible = true
        field.set(this, launcher)
    }

    /**
     * Mirrors the body of the lambda registered with the takePhotoLauncher, exercising
     * the same private methods the production callback calls.
     */
    fun simulatePhotoResult(
        resultCode: Int,
        photoBase64: String?,
    ) {
        if (resultCode == Activity.RESULT_OK && photoBase64 != null) {
            setPhotoAsBase64(photoBase64)
            invokePrivate("displayImage", String::class.java to photoBase64)
            invokePrivate("enableSaveButton")
        }
        invokePrivate("enableTakePhotoButton")
    }

    private fun invokePrivate(
        methodName: String,
        vararg args: Pair<Class<*>, Any?>,
    ) {
        val method =
            PersonalIdPhotoCaptureFragment::class.java
                .getDeclaredMethod(methodName, *args.map { it.first }.toTypedArray())
        method.isAccessible = true
        method.invoke(this, *args.map { it.second }.toTypedArray())
    }

    fun invokePhotoUploadSuccess(photoBase64: String) {
        setPhotoAsBase64(photoBase64)
        val method =
            PersonalIdPhotoCaptureFragment::class.java
                .getDeclaredMethod("onPhotoUploadSuccess", String::class.java)
        method.isAccessible = true
        method.invoke(this, photoBase64)
    }

    fun invokeCompleteProfileFailure(
        failureCode: PersonalIdOrConnectApiErrorCodes,
        t: Throwable?,
    ) {
        val method =
            PersonalIdPhotoCaptureFragment::class.java
                .getDeclaredMethod(
                    "onCompleteProfileFailure",
                    PersonalIdOrConnectApiErrorCodes::class.java,
                    Throwable::class.java,
                )
        method.isAccessible = true
        method.invoke(this, failureCode, t)
    }

    fun setPhotoAsBase64(photoBase64: String?) {
        val field = PersonalIdPhotoCaptureFragment::class.java.getDeclaredField("photoAsBase64")
        field.isAccessible = true
        field.set(this, photoBase64)
    }
}
