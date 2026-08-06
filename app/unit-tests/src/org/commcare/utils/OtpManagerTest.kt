package org.commcare.utils

import android.app.Activity
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.util.FirebaseTestUtils
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Verifies which [OtpAuthService] each [OtpManager] constructor resolves to. The two-step
 * resolution matters because the fragment relies on the 3-arg constructor deriving the method
 * from the session data rather than always meaning Firebase.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class OtpManagerTest {
    @Before
    fun initializeFirebase() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
    }

    private fun sessionDataWithSmsMethod(smsMethod: String?) = PersonalIdSessionData().apply { this.smsMethod = smsMethod }

    private fun authServiceOf(otpManager: OtpManager): OtpAuthService {
        val field = OtpManager::class.java.getDeclaredField("authService")
        field.isAccessible = true
        return field.get(otpManager) as OtpAuthService
    }

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun callback(): OtpVerificationCallback = mock(OtpVerificationCallback::class.java)

    @Test
    fun `explicit personal_id method resolves to PersonalIdAuthService`() {
        val manager =
            OtpManager(
                activity(),
                sessionDataWithSmsMethod(OtpManager.SMS_METHOD_FIREBASE),
                callback(),
                OtpManager.SMS_METHOD_PERSONAL_ID,
            )
        assertTrue(authServiceOf(manager) is PersonalIdAuthService)
    }

    @Test
    fun `session sms method of personal_id resolves to PersonalIdAuthService`() {
        val manager =
            OtpManager(
                activity(),
                sessionDataWithSmsMethod(OtpManager.SMS_METHOD_PERSONAL_ID),
                callback(),
            )
        assertTrue(authServiceOf(manager) is PersonalIdAuthService)
    }

    @Test
    fun `session sms method is matched case-insensitively`() {
        val manager =
            OtpManager(
                activity(),
                sessionDataWithSmsMethod("Personal_ID"),
                callback(),
            )
        assertTrue(authServiceOf(manager) is PersonalIdAuthService)
    }

    @Test
    fun `session sms method of firebase resolves to FirebaseAuthService`() {
        val manager =
            OtpManager(
                activity(),
                sessionDataWithSmsMethod(OtpManager.SMS_METHOD_FIREBASE),
                callback(),
            )
        assertTrue(authServiceOf(manager) is FirebaseAuthService)
    }

    @Test
    fun `null session sms method resolves to FirebaseAuthService`() {
        val manager =
            OtpManager(
                activity(),
                sessionDataWithSmsMethod(null),
                callback(),
            )
        assertTrue(authServiceOf(manager) is FirebaseAuthService)
    }
}
