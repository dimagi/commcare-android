package org.commcare.fragments.personalId

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Button
import androidx.annotation.CallSuper
import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.connect.network.ApiPersonalId
import org.commcare.connect.network.IApiCallback
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler
import org.commcare.dalvik.R
import org.commcare.fragments.MicroImageActivity
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.commcare.utils.MediaUtil
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.junit.After
import org.junit.Before
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdPhotoCaptureFragment tests.
 */
abstract class BasePersonalIdPhotoCaptureFragmentTest : BasePersonalIdConfigurationTest<TestablePersonalIdPhotoCaptureFragment>() {
    protected lateinit var mocksCloseable: AutoCloseable

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
    @CallSuper
    override fun setUp() {
        super.setUp()
        mocksCloseable = MockitoAnnotations.openMocks(this)
        MockAndroidKeyStoreProvider.registerProvider()
        Intents.init()
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
        launchFragmentForTest(sessionData, R.id.personalid_photo_capture) {
            TestablePersonalIdPhotoCaptureFragment(it)
        }
    }

    protected fun intendPhotoCaptureResult(
        resultCode: Int,
        photoBase64: String?,
    ) {
        val data =
            photoBase64?.let {
                Intent().putExtra(MicroImageActivity.MICRO_IMAGE_BASE_64_RESULT_KEY, it)
            }
        Intents
            .intending(hasComponent(MicroImageActivity::class.java.name))
            .respondWith(Instrumentation.ActivityResult(resultCode, data))
    }

    protected fun takePhoto(photoBase64: String) {
        intendPhotoCaptureResult(Activity.RESULT_OK, photoBase64)
        clickButton(R.id.take_photo_button)
    }

    protected fun clickSavePhoto() = clickButton(R.id.save_photo_button)

    protected fun clickButton(
        @IdRes buttonId: Int,
    ) {
        val button = fragment.requireView().findViewById<Button>(buttonId)
        activity.runOnUiThread { button.performClick() }
        ShadowLooper.idleMainLooper()
    }

    private fun captureCompleteProfileApiCallback(): IApiCallback {
        val captor = ArgumentCaptor.forClass(IApiCallback::class.java)
        apiPersonalIdMock.verify {
            ApiPersonalId.setPhotoAndCompleteProfile(
                any(),
                any(),
                any(),
                any(),
                any(),
                captor.capture(),
            )
        }
        return captor.value
    }

    protected fun deliverCompleteProfileSuccess() {
        val responseJson =
            """
            {
                "username": "${testSessionData.personalId}",
                "db_key": "${testSessionData.dbKey}",
                "password": "${testSessionData.oauthPassword}"
            }
            """.trimIndent()
        val callback = captureCompleteProfileApiCallback()
        activity.runOnUiThread {
            callback.processSuccess(200, responseJson.byteInputStream())
        }
        ShadowLooper.idleMainLooper()
    }

    protected fun deliverCompleteProfileFailure(
        responseCode: Int,
        errorBody: String,
        t: Throwable = RuntimeException("test failure"),
    ) {
        val callback = captureCompleteProfileApiCallback()
        activity.runOnUiThread {
            callback.processFailure(responseCode, "test-url", errorBody, t)
        }
        ShadowLooper.idleMainLooper()
    }

    @After
    @CallSuper
    override fun tearDown() {
        val errors = mutableListOf<Throwable>()
        listOf(
            { activityController.pause().stop().destroy() },
            { Intents.release() },
            { errorHandlerMock.close() },
            { apiPersonalIdMock.close() },
            { mediaUtilMock.close() },
            { firebaseAnalyticsUtilMock.close() },
            { connectUserDatabaseUtilMock.close() },
            { connectDatabaseHelperMock.close() },
            { personalIdManagerMock.close() },
            { mocksCloseable.close() },
            { MockAndroidKeyStoreProvider.deregisterProvider() },
            { super.tearDown() },
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
 * Testable subclass of PersonalIdPhotoCaptureFragment that exposes an injectable NavController
 */
class TestablePersonalIdPhotoCaptureFragment(
    private val testNavController: NavController? = null,
) : PersonalIdPhotoCaptureFragment() {
    override fun getNavController(): NavController = testNavController ?: super.getNavController()
}
