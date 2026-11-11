package org.commcare.utils

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.core.network.AuthInfo
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Verifies which [OtpAuthService] the method string passed to [OtpManager] resolves to.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class OtpManagerTest {
    @Before
    fun initializeFirebase() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
    }

    private val authInfo = AuthInfo.TokenAuth("test-session-token")

    private fun authServiceOf(otpManager: OtpManager): OtpAuthService {
        val field = OtpManager::class.java.getDeclaredField("authService")
        field.isAccessible = true
        return field.get(otpManager) as OtpAuthService
    }

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun callback(): OtpVerificationCallback = mock(OtpVerificationCallback::class.java)

    private fun managerFor(method: String?) = OtpManager(activity(), authInfo, callback(), method)

    @Test
    fun `personal_id method resolves to PersonalIdAuthService`() {
        assertTrue(authServiceOf(managerFor(OtpManager.SMS_METHOD_PERSONAL_ID)) is PersonalIdAuthService)
    }

    @Test
    fun `method is matched case-insensitively`() {
        assertTrue(authServiceOf(managerFor("Personal_ID")) is PersonalIdAuthService)
    }

    @Test
    fun `firebase method resolves to FirebaseAuthService`() {
        assertTrue(authServiceOf(managerFor(OtpManager.SMS_METHOD_FIREBASE)) is FirebaseAuthService)
    }

    @Test
    fun `null method resolves to FirebaseAuthService`() {
        assertTrue(authServiceOf(managerFor(null)) is FirebaseAuthService)
    }

    @Test
    fun `basic auth is accepted for the profile flow`() {
        val manager =
            OtpManager(
                activity(),
                AuthInfo.ProvidedAuth("test-user-id", "test-password", false),
                callback(),
                OtpManager.SMS_METHOD_PERSONAL_ID,
            )
        assertTrue(authServiceOf(manager) is PersonalIdAuthService)
    }
}
