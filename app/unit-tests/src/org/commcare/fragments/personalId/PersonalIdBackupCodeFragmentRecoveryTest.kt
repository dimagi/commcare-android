package org.commcare.fragments.personalId

import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.ConnectConstants
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.database.ConnectUserDatabaseUtil
import org.commcare.dalvik.R
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests [PersonalIdBackupCodeFragment] in account recovery mode.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdBackupCodeFragmentRecoveryTest : BasePersonalIdBackupCodeFragmentTest() {
    private lateinit var connectDatabaseHelperMock: MockedStatic<ConnectDatabaseHelper>
    private lateinit var connectUserDatabaseUtilMock: MockedStatic<ConnectUserDatabaseUtil>

    @Before
    override fun setUp() {
        super.setUp()
        launchBackupCodeFragment(buildSessionData(accountExists = true, photoBase64 = TEST_PHOTO_BASE64))
        // Recovery success writes the account to the DB; stub those statics so no real storage is touched.
        connectDatabaseHelperMock = Mockito.mockStatic(ConnectDatabaseHelper::class.java)
        connectUserDatabaseUtilMock = Mockito.mockStatic(ConnectUserDatabaseUtil::class.java)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        connectUserDatabaseUtilMock.close()
        connectDatabaseHelperMock.close()
    }

    // ========== Initial State ==========

    @Test
    fun `screen uses the confirm-code title`() {
        assertEquals(fragment.getString(R.string.connect_backup_code_title_confirm), activity.title.toString())
    }

    @Test
    fun `heading and subtitle use the recovery copy`() {
        assertEquals(
            fragment.getString(R.string.connect_backup_code_message_title),
            heading.text.toString(),
        )
        assertEquals(
            fragment.getString(R.string.connect_backup_code_message),
            subtitle.text.toString(),
        )
    }

    @Test
    fun `confirm code field is hidden in recovery mode`() {
        assertEquals(View.GONE, confirmCodeLabel.visibility)
        assertEquals(View.GONE, confirmCodeLayout.visibility)
    }

    @Test
    fun `welcome back header greets the user by name`() {
        assertEquals(View.VISIBLE, welcomeBackLayout.visibility)
        assertEquals(
            fragment.getString(R.string.personalid_welcome_back_msg, TEST_USER_NAME),
            welcomeBackText.text.toString(),
        )
    }

    @Test
    fun `the session photo is rendered`() {
        // The layout's android:src is a vector placeholder, so a BitmapDrawable can only have come
        // from setImageBitmap(), and the shadow records the bytes the bitmap was decoded from.
        val renderedPhoto = userPhoto.drawable as? BitmapDrawable
        assertNotNull("Placeholder should be replaced by a decoded bitmap", renderedPhoto)
        assertArrayEquals(
            "Rendered bitmap should be decoded from the session's base64 photo",
            Base64.decode(TEST_PHOTO_BASE64, Base64.DEFAULT),
            shadowOf(renderedPhoto!!.bitmap).createdFromBytes,
        )
    }

    @Test
    fun `continue is disabled until the code is complete`() {
        assertFalse("Continue should be disabled initially", continueButton.isEnabled)

        enterBackupCode("12345")

        assertFalse("Continue should stay disabled with fewer than 6 digits", continueButton.isEnabled)

        enterBackupCode(TEST_BACKUP_CODE)

        // A complete code enables continue and immediately auto-submits, which disables the button
        // again for the duration of the request. Auto-submit is itself gated on the button being
        // enabled, so the outgoing request is the observable proof that the complete code enabled it.
        takeRequestOrFail()
        assertFalse("Continue should be disabled while the auto-submitted request is in flight", continueButton.isEnabled)
    }

    // ========== Request ==========

    @Test
    fun `a complete code posts it to the confirm endpoint and disables continue while in flight`() {
        // No response is enqueued so the request stays in flight, making the disabled assertion deterministic.
        enterBackupCode(TEST_BACKUP_CODE)

        val request = takeRequestOrFail()
        assertEquals("/users/recover/confirm_backup_code", request.path)
        assertEquals("POST", request.method)
        assertEquals(TEST_BACKUP_CODE, JSONObject(request.body.readUtf8()).getString("recovery_pin"))

        val authHeader = request.headers["Authorization"]
        assertNotNull("Authorization header should be present", authHeader)
        assertTrue(
            "Authorization header should be a token-auth using the session token",
            authHeader!!.contains(TEST_SESSION_TOKEN),
        )

        assertFalse("Continue should be disabled while the request is in flight", continueButton.isEnabled)
    }

    // ========== Success ==========

    @Test
    fun `a confirmed code stores the account and navigates to the recovery success screen`() {
        mockWebServer.enqueue(successResponse())

        enterBackupCode(TEST_BACKUP_CODE)
        drainHttp()

        connectDatabaseHelperMock.verify {
            ConnectDatabaseHelper.handleReceivedDbPassphrase(Mockito.any(), Mockito.eq("test-db-key"))
        }

        val userCaptor = ArgumentCaptor.forClass(ConnectUserRecord::class.java)
        connectUserDatabaseUtilMock.verify {
            ConnectUserDatabaseUtil.storeUser(Mockito.any(), userCaptor.capture())
        }
        val storedUser = userCaptor.value
        assertEquals(TEST_USER_NAME, storedUser.name)
        assertEquals("test-personal-id", storedUser.userId)
        assertEquals(TEST_PHONE_NUMBER, storedUser.primaryPhone)
        assertEquals(TEST_PHOTO_BASE64, storedUser.photo)
        assertEquals(PersonalIdSessionData.PIN, storedUser.requiredLock)
        // Recovery never writes the entered code onto the session data, so the stored record has no pin.
        assertNull(storedUser.pin)

        assertMessageDisplay(
            title = fragment.getString(R.string.connect_recovery_success_title),
            message = fragment.getString(R.string.connect_recovery_success_message),
            phase = ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
        )
    }

    @Test
    fun `a confirmed code routes to the email screen when the toggle is active and no email is on file`() {
        activateEmailOtpToggle()
        mockWebServer.enqueue(successResponse())

        enterBackupCode(TEST_BACKUP_CODE)
        drainHttp()

        assertEquals(R.id.personalid_email, navController.currentDestination?.id)
        assertEquals(
            EmailWorkFlow.RECOVERY,
            navController
                .backStack
                .last()
                .arguments
                ?.getSerializable("workflow"),
        )
        connectUserDatabaseUtilMock.verifyNoInteractions()
    }

    @Test
    fun `a confirmed code finalizes recovery when the server already has a verified email`() {
        activateEmailOtpToggle()
        mockWebServer.enqueue(successResponse(email = "user@example.com"))

        enterBackupCode(TEST_BACKUP_CODE)
        drainHttp()

        connectUserDatabaseUtilMock.verify {
            ConnectUserDatabaseUtil.storeUser(Mockito.any(), Mockito.any())
        }
        assertMessageDisplay(
            title = fragment.getString(R.string.connect_recovery_success_title),
            message = fragment.getString(R.string.connect_recovery_success_message),
            phase = ConnectConstants.PERSONALID_RECOVERY_SUCCESS,
        )
    }

    // ========== Failure ==========

    @Test
    fun `a wrong code clears the field and navigates to the wrong-code message`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"attempts_left":2}"""))

        enterBackupCode(TEST_BACKUP_CODE)
        drainHttp()

        assertTrue("Entered code should be cleared for the next attempt", backupCodeView.codeValue.isEmpty())
        assertMessageDisplay(
            title = fragment.getString(R.string.connect_backup_fail_title),
            message = fragment.getString(R.string.personalid_wrong_backup_message, 2),
            phase = ConnectConstants.PERSONALID_RECOVERY_WRONG_BACKUPCODE,
        )
        connectUserDatabaseUtilMock.verifyNoInteractions()
    }

    @Test
    fun `a locked account navigates to the configuration-failed screen`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":"LOCKED_ACCOUNT"}"""))

        enterBackupCode(TEST_BACKUP_CODE)
        drainHttp()

        assertMessageDisplay(
            title = fragment.getString(R.string.personalid_configuration_process_failed_title),
            message = fragment.getString(R.string.personalid_configuration_locked_account),
            phase = ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED,
        )
    }

    @Test
    fun `a retryable failure shows the error and re-enables continue`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        enterBackupCode(TEST_BACKUP_CODE)
        drainHttp()

        assertEquals(R.id.personalid_backup_code, navController.currentDestination?.id)
        assertEquals(View.VISIBLE, errorMessage.visibility)
        assertEquals(
            fragment.getString(R.string.recovery_network_server_error),
            errorText.text.toString(),
        )
        assertTrue("Continue should be re-enabled so the user can retry", continueButton.isEnabled)
    }

    @Test
    fun `a non-retryable failure shows the error and leaves continue disabled`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        enterBackupCode(TEST_BACKUP_CODE)
        drainHttp()

        assertEquals(R.id.personalid_backup_code, navController.currentDestination?.id)
        assertEquals(View.VISIBLE, errorMessage.visibility)
        assertEquals(
            fragment.getString(R.string.network_forbidden_error),
            errorText.text.toString(),
        )
        assertFalse("Continue should stay disabled on a non-retryable failure", continueButton.isEnabled)
    }

    // ========== Mode Switch ==========

    @Test
    fun `not me switches the screen to set-code mode`() {
        enterBackupCode("123")

        assertEquals(View.GONE, notMeButton.visibility)
        clickView(notMeButton)

        assertEquals(false, sessionData.accountExists)
        assertEquals(View.VISIBLE, confirmCodeLabel.visibility)
        assertEquals(View.VISIBLE, confirmCodeLayout.visibility)
        assertEquals(View.GONE, welcomeBackLayout.visibility)
        assertEquals(
            fragment.getString(R.string.connect_backup_code_remember, 6),
            subtitle.text.toString(),
        )
        assertTrue("Entered code should be cleared when switching modes", backupCodeView.codeValue.isEmpty())
    }

    // ========== Helpers ==========

    private fun successResponse(email: String? = null): MockResponse {
        val body =
            JSONObject().apply {
                put("username", "test-personal-id")
                put("db_key", "test-db-key")
                put("password", "test-oauth-pwd")
                if (email != null) put("email", email)
            }
        return MockResponse().setResponseCode(200).setBody(body.toString())
    }

    private fun assertMessageDisplay(
        title: String,
        message: String,
        phase: Int,
    ) {
        assertEquals(R.id.personalid_message_display, navController.currentDestination?.id)
        val args = navController.backStack.last().arguments
        assertEquals(title, args?.getString("title"))
        assertEquals(message, args?.getString("message"))
        assertEquals(phase, args?.getInt("callingClass"))
        assertEquals(false, args?.getBoolean("isCancellable"))
    }

    companion object {
        // Base64 for "photo" — decodable so MediaUtil produces a bitmap.
        private const val TEST_PHOTO_BASE64 = "cGhvdG8="
    }
}
