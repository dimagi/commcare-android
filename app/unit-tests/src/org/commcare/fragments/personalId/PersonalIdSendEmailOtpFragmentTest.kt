package org.commcare.fragments.personalId

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.dalvik.R
import org.commcare.personalId.profile.BasePersonalIdProfileTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdSendEmailOtpFragmentTest : BasePersonalIdProfileTest() {
    private val fragmentArgs =
        Bundle().apply {
            putString("email", "user@example.com")
            putBoolean("masked", true)
            putSerializable("workflow", EmailWorkFlow.FORGOT_BACKUP_CODE_EXISTING_USER)
        }

    @Before
    fun navigateToSendEmailOtp() {
        onUiThread {
            navController.navigate(R.id.personalid_send_email_otp_fragment, fragmentArgs)
        }
    }

    private fun fragment() =
        navHostFragment.childFragmentManager
            .primaryNavigationFragment as PersonalIdSendEmailOtpFragment

    private fun sendButton(): MaterialButton = fragment().requireView().findViewById(R.id.personalid_send_email_otp_button)

    private fun emailText(): TextView = fragment().requireView().findViewById(R.id.personalid_send_email_otp_address)

    private fun errorText(): TextView = fragment().requireView().findViewById(R.id.personalid_send_email_otp_error)

    @Test
    fun `masked email is displayed correctly`() {
        assertEquals("u***r@example.com", emailText().text.toString())
    }

    @Test
    fun `send button is enabled initially`() {
        assertTrue(sendButton().isEnabled)
    }

    @Test
    fun `error is hidden initially`() {
        assertEquals(View.GONE, errorText().visibility)
    }

    @Test
    fun `send button posts to send_email_otp endpoint`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        onUiThread { sendButton().performClick() }

        val request = mockApiServer.takeRequestOrFail()
        assertEquals("/users/send_email_otp", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun `successful send navigates to email verification`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val testNavController = TestNavHostController(ApplicationProvider.getApplicationContext())
        onUiThread {
            testNavController.setGraph(R.navigation.nav_graph_personalid_profile)
            testNavController.setCurrentDestination(R.id.personalid_send_email_otp_fragment, fragmentArgs)
            Navigation.setViewNavController(fragment().requireView(), testNavController)
            sendButton().performClick()
        }
        mockApiServer.drainHttp()

        assertEquals(R.id.personalid_email_verification_forgot_backup_code_fragment, testNavController.currentDestination!!.id)
    }

    @Test
    fun `failed send shows error and re-enables button`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        onUiThread { sendButton().performClick() }
        mockApiServer.drainHttp()

        assertEquals(View.VISIBLE, errorText().visibility)
        assertTrue(sendButton().isEnabled)
    }
}
