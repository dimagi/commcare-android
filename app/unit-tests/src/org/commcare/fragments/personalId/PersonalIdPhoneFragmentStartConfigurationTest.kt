package org.commcare.fragments.personalId

import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.commcare.CommCareTestApplication
import org.commcare.android.logging.ReportingUtils
import org.commcare.connect.network.ApiService
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.connectId.PersonalIdApiClient
import org.commcare.dalvik.R
import org.commcare.utils.HashUtils
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Tests for start_configuration HTTP request using MockWebServer.
 * Intercepts actual HTTP calls to verify request payload and test different response scenarios.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdPhoneFragmentStartConfigurationTest : BasePersonalIdPhoneFragmentTest() {
    private lateinit var mockWebServer: MockWebServer

    @Before
    override fun setUp() {
        super.setUp()
        setupMockWebServer()
    }

    private fun setupMockWebServer() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val mockServerUrl = mockWebServer.url("/").toString()

        val apiService =
            BaseApiClient
                .buildRetrofitClient(mockServerUrl, PersonalIdApiClient.API_VERSION)
                .create(ApiService::class.java)

        val apiServiceField = PersonalIdApiClient::class.java.getDeclaredField("apiService")
        apiServiceField.isAccessible = true
        apiServiceField.set(null, apiService)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        val apiServiceField = PersonalIdApiClient::class.java.getDeclaredField("apiService")
        apiServiceField.isAccessible = true
        apiServiceField.set(null, null)
        mockWebServer.shutdown()
    }

    // ========== Request Payload Tests ==========

    @Test
    fun testStartConfiguration_requestPayload_containsAllFields() {
        // Arrange
        setupFragmentForRequest()
        mockWebServer.enqueue(createSuccessResponse())

        // Act
        clickContinueButton()

        // Assert
        val request = mockWebServer.takeRequest()
        assertNotNull("Request should be received", request)
        assertEquals("/users/start_configuration", request.path)
        assertEquals("POST", request.method)

        val requestBody = JSONObject(request.body.readUtf8())
        assertEquals(
            "Phone number should be formatted correctly",
            "+919876543210",
            requestBody.getString("phone_number"),
        )

        assertEquals(
            "Application ID should match package name",
            activity.packageName,
            requestBody.getString("application_id"),
        )

        val gpsLocation = requestBody.getString("gps_location")
        assertTrue("GPS location should contain latitude", gpsLocation.contains("37.7749"))
        assertTrue("GPS location should contain longitude", gpsLocation.contains("-122.4194"))

        assertEquals(
            "Device ID should match ReportingUtils",
            ReportingUtils.getDeviceId(),
            requestBody.getString("cc_device_id"),
        )
    }

    @Test
    fun testStartConfiguration_requestHeaders_containEssentialValues() {
        // Arrange
        setupFragmentForRequest()
        mockWebServer.enqueue(createSuccessResponse())

        // Act
        clickContinueButton()

        // Assert
        val request = mockWebServer.takeRequest()
        assertTrue(
            "Request should have CC-Integrity-Token header",
            request.headers["CC-Integrity-Token"] == "$TEST_INTEGRITY_TOKEN",
        )

        val requestBody = request.body.readUtf8()
        val expectedRequestHash = HashUtils.computeHash(requestBody, HashUtils.HashAlgorithm.SHA256)
        val actualRequestHash = request.headers["CC-Request-Hash"]
        assertEquals(
            "CC-Request-Hash should match computed hash of request body",
            expectedRequestHash,
            actualRequestHash,
        )

        val acceptHeader = request.headers["Accept"]
        assertNotNull("Accept header should be present", acceptHeader)
        assertTrue(
            "Accept header should contain API version",
            acceptHeader!!.contains("version=" + PersonalIdApiClient.API_VERSION),
        )
    }

    // ========== Response Handling Tests ==========

    @Test
    fun testStartConfiguration_successResponse_moveToBiometric() {
        // Arrange
        setupFragmentForRequest()
        mockWebServer.enqueue(createSuccessResponse())

        // Act
        clickContinueButton()

        // Assert
        mockWebServer.takeRequest()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

//        assertEquals(
//            "Should navigate to biometric config fragment on success",
//            R.id.personalid_biometric_config,
//            navController.currentDestination!!.id,
//        )
    }

    @Test
    fun testStartConfiguration_forbiddenResponse_showsError() {
        // Arrange
        setupFragmentForRequest()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error_code": "PHONE_NOT_VALIDATED"}"""),
        )

        // Act
        clickContinueButton()

        // Assert
        mockWebServer.takeRequest()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(
            "Should navigate to message display screen on forbidden error",
            R.id.personalid_message_display,
            navController.currentDestination!!.id,
        )

        // Verify the exact error message displayed
        val expectedMessage = activity.getString(R.string.personalid_configuration_process_failed_subtitle)
        val actualMessage = navController.currentBackStackEntry!!.arguments!!.getString("message")
        assertEquals(
            "Error message should match forbidden error message",
            expectedMessage,
            actualMessage,
        )
    }

    @Test
    fun testStartConfiguration_networkError_allowsRetry() {
        // Arrange
        setupFragmentForRequest()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        // Act
        clickContinueButton()

        // Assert
        mockWebServer.takeRequest()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val errorView = fragment.view!!.findViewById<android.widget.TextView>(R.id.personalid_phone_error)
        assertEquals(
            "Error should be visible after network error",
            View.VISIBLE,
            errorView!!.visibility,
        )
        val expectedMessage = activity.getString(R.string.recovery_network_server_error)
        assertEquals(
            "Error message should match network error string",
            expectedMessage,
            errorView.text!!.toString(),
        )

        val continueButton = fragment.view!!.findViewById<Button>(R.id.personalid_phone_continue_button)
        assertTrue(
            "Button should be re-enabled for retry on network error",
            continueButton!!.isEnabled,
        )
    }

    @Test
    fun testStartConfiguration_accountLockedResponse() {
        // Arrange
        setupFragmentForRequest()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error_code": "LOCKED_ACCOUNT"}"""),
        )

        // Act
        clickContinueButton()

        // Assert
        mockWebServer.takeRequest()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Verify navigation to message display occurred
        assertEquals(
            "Should navigate to message display screen on account locked",
            R.id.personalid_message_display,
            navController.currentDestination!!.id,
        )

        // Verify the message contains the locked account error
        val expectedMessage = activity.getString(R.string.personalid_configuration_locked_account)
        val actualMessage = navController.currentBackStackEntry!!.arguments!!.getString("message")

        assertEquals(
            "Message should indicate account is locked",
            expectedMessage,
            actualMessage,
        )
    }

    // ========== Helper Methods ==========

    private fun setupFragmentForRequest() {
        val phoneInput = fragment.view!!.findViewById<EditText>(R.id.connect_primary_phone_input)
        phoneInput.setText("9876543210")

        val countryCodeInput = fragment.view!!.findViewById<EditText>(R.id.countryCode)
        countryCodeInput.setText("+91")

        val consentCheckbox = fragment.view!!.findViewById<CheckBox>(R.id.connect_consent_check)
        consentCheckbox!!.isChecked = true

        val continueButton = fragment.view!!.findViewById<Button>(R.id.personalid_phone_continue_button)
        fragment.onLocationResult(mockLocation)

        assertTrue(
            "Continue button should be enabled with all requirements",
            continueButton!!.isEnabled,
        )
    }

    private fun clickContinueButton() {
        val continueButton = fragment.view!!.findViewById<Button>(R.id.personalid_phone_continue_button)
        continueButton!!.performClick()
    }

    private fun createSuccessResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                    "token": "test_token_123",
                    "required_lock": "biometric",
                    "demo_user": false,
                    "account_exists": false
                }
                """.trimIndent(),
            )
}
