package org.commcare.fragments.personalId

import androidx.annotation.CallSuper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.navigation.NavController
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.PersonalIdManager
import org.commcare.dalvik.R
import org.commcare.utils.MockAndroidKeyStoreProvider
import org.junit.After
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

/**
 * Base test class for PersonalIdBiometricConfigFragment tests.
 * Contains common setup and teardown logic for fragment testing.
 */
abstract class BasePersonalIdBiometricConfigFragmentTest :
    BasePersonalIdConfigurationTest<TestablePersonalIdBiometricConfigFragment>() {
    protected lateinit var mocksCloseable: AutoCloseable
    protected lateinit var personalIdManagerMock: MockedStatic<PersonalIdManager>

    @Mock
    protected lateinit var mockBiometricManager: BiometricManager

    @Mock
    protected lateinit var mockPersonalIdManager: PersonalIdManager

    @Before
    @CallSuper
    override fun setUp() {
        super.setUp()
        mocksCloseable = MockitoAnnotations.openMocks(this)
        MockAndroidKeyStoreProvider.registerProvider()
        mockBiometricManager()
    }

    private fun mockBiometricManager() {
        personalIdManagerMock = Mockito.mockStatic(PersonalIdManager::class.java)
        personalIdManagerMock
            .`when`<PersonalIdManager> { PersonalIdManager.getInstance() }
            .thenReturn(mockPersonalIdManager)
        Mockito
            .`when`(mockPersonalIdManager.getBiometricManager(Mockito.any()))
            .thenReturn(mockBiometricManager)
    }

    protected fun setUpBiometricFragment(requiredLock: String = PersonalIdSessionData.PIN) {
        val sessionData =
            PersonalIdSessionData(
                requiredLock = requiredLock,
                demoUser = false,
            )
        launchFragmentForTest(sessionData, R.id.personalid_biometric_config) {
            TestablePersonalIdBiometricConfigFragment(it)
        }
    }

    @After
    @CallSuper
    override fun tearDown() {
        activityController.pause().stop().destroy()
        mocksCloseable.close()
        personalIdManagerMock.close()
        MockAndroidKeyStoreProvider.deregisterProvider()
        super.tearDown()
    }
}

/**
 * Testable subclass of PersonalIdBiometricConfigFragment that allows injecting a mock BiometricManager
 * and a test NavController for navigation testing.
 */
class TestablePersonalIdBiometricConfigFragment(
    private val testNavController: NavController? = null,
) : PersonalIdBiometricConfigFragment() {
    override fun getNavController(): NavController = testNavController ?: super.getNavController()

    /**
     * Simulates a successful biometric authentication by directly triggering the authentication callback.
     */
    fun simulateSuccessfulAuthentication() {
        val callbackField = PersonalIdBiometricConfigFragment::class.java.getDeclaredField("biometricCallback")
        callbackField.isAccessible = true
        val callback = callbackField.get(this) as BiometricPrompt.AuthenticationCallback
        val result = Mockito.mock(BiometricPrompt.AuthenticationResult::class.java)
        callback.onAuthenticationSucceeded(result)
    }

    /**
     * Simulates a failed biometric authentication by directly triggering the authentication callback.
     */
    fun simulateFailedAuthentication() {
        val callbackField = PersonalIdBiometricConfigFragment::class.java.getDeclaredField("biometricCallback")
        callbackField.isAccessible = true
        val callback = callbackField.get(this) as BiometricPrompt.AuthenticationCallback
        callback.onAuthenticationFailed()
    }
}
