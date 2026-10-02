package org.commcare.personalId.profile

import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import org.commcare.CommCareTestApplication
import org.commcare.android.shadows.ShadowPhoneAuthProvider
import org.commcare.android.util.FirebaseTestUtils
import org.commcare.dalvik.R
import org.commcare.personalId.profile.PersonalIdProfileSendPhoneOtpFragment.Companion.maskPhone
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(
    application = CommCareTestApplication::class,
    sdk = [31],
    shadows = [ShadowPhoneAuthProvider::class],
)
@RunWith(AndroidJUnit4::class)
class PersonalIdProfileSendPhoneOtpFragmentTest : BasePersonalIdProfileTest() {
    private val pendingEmail = "grace@example.com"

    @Before
    fun resetFirebase() {
        FirebaseTestUtils.initializeDefaultAppIfNeeded()
        ShadowPhoneAuthProvider.reset()
    }

    private fun args() =
        PersonalIdProfileSendPhoneOtpFragmentArgs
            .Builder(pendingEmail)
            .build()
            .toBundle()

    private fun launch() {
        onUiThread { navController.navigate(R.id.personalid_profile_send_phone_otp_fragment, args()) }
    }

    private fun currentView() = navHostFragment.childFragmentManager.primaryNavigationFragment!!.requireView()

    private fun sendButton() = currentView().findViewById<MaterialButton>(R.id.personalid_send_email_otp_button)

    private fun text(id: Int) = currentView().findViewById<TextView>(id).text.toString()

    @Test
    fun `shows the stored phone number masked to the last 3 digits`() {
        launch()
        assertEquals("•••••••••890", text(R.id.personalid_send_email_otp_address))
    }

    @Test
    fun `masks all but the last 3 characters`() {
        assertEquals("•••••••••890", maskPhone("+11234567890"))
    }

    @Test
    fun `keeps 3 characters when the value has 4`() {
        assertEquals("•234", maskPhone("1234"))
    }

    @Test
    fun `fully masks a value of 3 characters or fewer`() {
        assertEquals("•••", maskPhone("123"))
        assertEquals("••", maskPhone("12"))
    }

    @Test
    fun `returns empty for empty or null`() {
        assertEquals("", maskPhone(""))
        assertEquals("", maskPhone(null))
    }

    @Test
    fun `shows the phone title and label`() {
        launch()
        assertEquals(
            activity.getString(R.string.personalid_send_phone_otp_title),
            text(R.id.personalid_send_email_otp_title),
        )
        assertEquals(
            activity.getString(R.string.personalid_profile_field_phone),
            text(R.id.personalid_send_email_otp_address_label),
        )
    }

    @Test
    fun `opening the screen sends no OTP`() {
        launch()
        assertEquals(0, ShadowPhoneAuthProvider.getRequestCount())
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `send code opens phone verification with the pending email`() {
        launch()
        onUiThread { sendButton().performClick() }

        assertEquals(R.id.personalid_profile_phone_verification_fragment, currentDestinationId())
        val args =
            PersonalIdProfilePhoneVerificationFragmentArgs.fromBundle(
                navHostFragment.childFragmentManager.primaryNavigationFragment!!.requireArguments(),
            )
        assertEquals(pendingEmail, args.pendingEmail)
    }

    @Test
    fun `double tap on send code navigates once`() {
        launch()
        val button = sendButton()
        onUiThread {
            button.performClick()
            button.performClick()
        }

        assertEquals(R.id.personalid_profile_phone_verification_fragment, currentDestinationId())
        onUiThread { navController.popBackStack() }
        assertEquals(R.id.personalid_profile_send_phone_otp_fragment, currentDestinationId())
    }
}
