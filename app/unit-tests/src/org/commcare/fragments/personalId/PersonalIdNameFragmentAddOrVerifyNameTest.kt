package org.commcare.fragments.personalId

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.CommCareTestApplication
import org.commcare.activities.connect.viewmodel.PersonalIdSessionDataViewModel
import org.commcare.connect.network.ApiService
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.connectId.PersonalIdApiClient
import org.commcare.dalvik.R
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
import java.util.concurrent.TimeUnit

/**
 * Tests the `addOrVerifyName` API integration of [PersonalIdNameFragment]. Uses MockWebServer to
 * intercept the actual HTTP request so we can assert both the request payload and the fragment's
 * response handling (navigation, error UI, retry-button state, session-data updates).
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class PersonalIdNameFragmentAddOrVerifyNameTest : BasePersonalIdNameFragmentTest() {
    private lateinit var mockWebServer: MockWebServer

    @Before
    override fun setUp() {
        super.setUp()
        setupMockWebServer()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        val apiServiceField = PersonalIdApiClient::class.java.getDeclaredField("apiService")
        apiServiceField.isAccessible = true
        apiServiceField.set(null, null)
        mockWebServer.shutdown()
    }

    private fun setupMockWebServer() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val apiService =
            BaseApiClient
                .buildRetrofitClient(mockWebServer.url("/").toString(), PersonalIdApiClient.API_VERSION)
                .create(ApiService::class.java)

        val apiServiceField = PersonalIdApiClient::class.java.getDeclaredField("apiService")
        apiServiceField.isAccessible = true
        apiServiceField.set(null, apiService)
    }

    // ========== Helpers ==========

    private fun successResponse(): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                    "account_exists": false,
                    "photo": null
                }
                """.trimIndent(),
            )

    private fun nameInput(): EditText = fragment.requireView().findViewById(R.id.nameTextValue)

    private fun continueButton(): MaterialButton = fragment.requireView().findViewById(R.id.personalid_name_continue_button)

    private fun errorView(): TextView = fragment.requireView().findViewById(R.id.personalid_name_error)

    private fun setName(value: String) {
        activity.runOnUiThread { nameInput().setText(value) }
        ShadowLooper.idleMainLooper()
    }

    private fun typeAndContinue(name: String) {
        setName(name)
        activity.runOnUiThread { continueButton().performClick() }
        ShadowLooper.idleMainLooper()
    }

    /**
     * Reads the next request with a bounded wait so a missing dispatch fails fast instead of
     * hanging the suite, then drains delayed UI work so callbacks can run before assertions.
     */
    private fun drainHttp() {
        takeRequestOrFail()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    private fun takeRequestOrFail(timeoutSeconds: Long = 5): RecordedRequest =
        mockWebServer.takeRequest(timeoutSeconds, TimeUnit.SECONDS)
            ?: throw AssertionError("Expected an HTTP request within ${timeoutSeconds}s but none arrived")

    // ========== Request payload ==========

    @Test
    fun `submitting a name sends a POST to check_name with the trimmed value`() {
        mockWebServer.enqueue(successResponse())

        typeAndContinue("  Margaret Hamilton  ")

        val request = takeRequestOrFail()
        assertEquals("/users/check_name", request.path)
        assertEquals("POST", request.method)

        val body = request.body.readUtf8()
        assertTrue("Body should carry the trimmed name", body.contains("\"name\":\"Margaret Hamilton\""))
        assertFalse("Body should not include the surrounding whitespace", body.contains("  Margaret Hamilton  "))
    }

    @Test
    fun `request authorization header uses the session token`() {
        mockWebServer.enqueue(successResponse())

        typeAndContinue("Ada Lovelace")

        val request = takeRequestOrFail()
        val authHeader = request.headers["Authorization"]
        assertNotNull("Authorization header should be present", authHeader)
        assertTrue(
            "Authorization header should be a token-auth using the session token",
            authHeader!!.contains(TEST_SESSION_TOKEN),
        )
    }

    // ========== Success path ==========

    @Test
    fun `a successful response navigates to the backup-code screen`() {
        mockWebServer.enqueue(successResponse())

        typeAndContinue("Ada Lovelace")

        drainHttp()

        assertEquals(R.id.personalid_backup_code, navController.currentDestination?.id)
    }

    @Test
    fun `a successful response stores the trimmed name on the session view model`() {
        mockWebServer.enqueue(successResponse())

        typeAndContinue("  Ada Lovelace  ")

        drainHttp()

        val viewModel = ViewModelProvider(activity)[PersonalIdSessionDataViewModel::class.java]
        assertEquals("Ada Lovelace", viewModel.personalIdSessionData.userName)
    }

    @Test
    fun `clicking continue immediately disables the button to prevent duplicate requests`() {
        // Enqueue but don't let the response settle — the button must already be disabled before any
        // dispatch completes.
        mockWebServer.enqueue(successResponse())

        setName("Ada Lovelace")
        assertTrue("Pre-click sanity check: button must be enabled before continue", continueButton().isEnabled)

        activity.runOnUiThread { continueButton().performClick() }
        // Drain only the immediately-posted UI work — running delayed tasks would let the
        // enqueued mock response settle and weaken the "immediately disabled" guarantee
        // this test exists to defend.
        ShadowLooper.idleMainLooper()

        assertFalse(
            "Continue button should be disabled the instant verifyOrAddName runs",
            continueButton().isEnabled,
        )
    }

    // ========== Retryable failures ==========

    @Test
    fun `a 500 server error shows the inline error and re-enables the continue button`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"),
        )

        typeAndContinue("Ada Lovelace")

        drainHttp()

        assertEquals(R.id.personalid_name, navController.currentDestination?.id)
        assertEquals(View.VISIBLE, errorView().visibility)
        assertEquals(
            activity.getString(R.string.recovery_network_server_error),
            errorView().text.toString(),
        )
        assertTrue(
            "Retryable failures should re-enable the continue button",
            continueButton().isEnabled,
        )
    }

    // ========== Non-retryable / routed-to-message-screen failures ==========

    @Test
    fun `a LOCKED_ACCOUNT error routes the user to the failure message screen`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error_code": "LOCKED_ACCOUNT"}"""),
        )

        typeAndContinue("Ada Lovelace")

        drainHttp()

        assertEquals(R.id.personalid_message_display, navController.currentDestination?.id)
        val args = navController.currentBackStackEntry?.arguments
        assertEquals(
            activity.getString(R.string.personalid_configuration_process_failed_title),
            args?.getString("title"),
        )
        assertEquals(
            activity.getString(R.string.personalid_configuration_locked_account),
            args?.getString("message"),
        )
    }

    @Test
    fun `a 403 PHONE_NOT_VALIDATED error shows the inline forbidden error and does not navigate`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error_code": "PHONE_NOT_VALIDATED"}"""),
        )

        typeAndContinue("Ada Lovelace")

        drainHttp()

        assertEquals(R.id.personalid_name, navController.currentDestination?.id)
        assertEquals(View.VISIBLE, errorView().visibility)
        assertEquals(
            activity.getString(R.string.network_forbidden_error),
            errorView().text.toString(),
        )
        assertFalse(
            "FORBIDDEN_ERROR is not retryable, so the button should stay disabled",
            continueButton().isEnabled,
        )
    }

    // ========== Enter-key shortcut ==========

    @Test
    fun `pressing enter with an empty name does not fire an API request`() {
        // Don't enqueue — any dispatch would trip MockWebServer with an unmatched request.
        activity.runOnUiThread { nameInput().onEditorAction(EditorInfo.IME_ACTION_DONE) }
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals("No HTTP traffic should be generated", 0, mockWebServer.requestCount)
        assertEquals(R.id.personalid_name, navController.currentDestination?.id)
    }

    @Test
    fun `pressing enter with a filled name triggers verifyOrAddName and navigates on success`() {
        mockWebServer.enqueue(successResponse())

        setName("Ada Lovelace")
        activity.runOnUiThread { nameInput().onEditorAction(EditorInfo.IME_ACTION_DONE) }
        ShadowLooper.idleMainLooper()

        drainHttp()

        assertEquals(R.id.personalid_backup_code, navController.currentDestination?.id)
    }
}
