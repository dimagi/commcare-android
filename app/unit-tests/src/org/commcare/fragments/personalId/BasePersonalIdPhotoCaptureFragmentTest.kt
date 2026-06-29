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
import okhttp3.mockwebserver.MockResponse
import org.commcare.activities.camera.MicroImageActivity
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.PersonalIdManager
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
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
import org.robolectric.shadows.ShadowLooper

/**
 * Base test class for PersonalIdPhotoCaptureFragment tests
 */
abstract class BasePersonalIdPhotoCaptureFragmentTest : BasePersonalIdConfigurationTest<TestablePersonalIdPhotoCaptureFragment>() {
    protected lateinit var mocksCloseable: AutoCloseable

    protected lateinit var personalIdManagerMock: MockedStatic<PersonalIdManager>
    protected lateinit var connectDatabaseHelperMock: MockedStatic<ConnectDatabaseHelper>
    protected lateinit var connectUserDatabaseUtilMock: MockedStatic<ConnectUserDatabaseUtil>
    protected lateinit var firebaseAnalyticsUtilMock: MockedStatic<FirebaseAnalyticsUtil>
    protected lateinit var mediaUtilMock: MockedStatic<MediaUtil>

    @Mock
    protected lateinit var mockPersonalIdManager: PersonalIdManager

    @Mock
    protected lateinit var mockBitmap: Bitmap

    // personalId/dbKey/oauthPassword are deliberately absent
    // The complete-profile response delivered by MockWebServer is the only source for them
    protected val testSessionData =
        PersonalIdSessionData(
            requiredLock = PersonalIdSessionData.PIN,
            demoUser = false,
            token = "test-token",
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

    protected fun enqueueCompleteProfileSuccess(
        personalId: String = "test-personal-id",
        dbKey: String = "test-db-key",
        oauthPassword: String = "test-oauth-pwd",
    ) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "username": "$personalId",
                        "db_key": "$dbKey",
                        "password": "$oauthPassword"
                    }
                    """.trimIndent(),
                ),
        )
    }

    protected fun enqueueCompleteProfileFailure(
        responseCode: Int,
        errorBody: String,
    ) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(responseCode)
                .setBody(errorBody),
        )
    }

    @After
    @CallSuper
    override fun tearDown() {
        val errors = mutableListOf<Throwable>()
        listOf(
            { activityController.pause().stop().destroy() },
            { Intents.release() },
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
