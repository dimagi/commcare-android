package org.commcare.connect.network.personalId

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import org.commcare.CommCareTestApplication
import org.commcare.connect.network.PersonalIdMockApiServer
import org.commcare.connect.network.base.IApiCallback
import org.commcare.core.network.AuthInfo
import org.commcare.network.HttpUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.annotation.Config

/**
 * The phone OTP endpoints serve both signup (PersonalID session token) and Manage Profile
 * (basic auth from the stored user). These tests pin the Authorization header each AuthInfo produces.
 */
@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ApiPersonalIdPhoneOtpAuthTest {
    private val mockApiServer = PersonalIdMockApiServer(PersonalIdMockApiServer.CallbackMode.DIRECT)
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val callback: IApiCallback = mock(IApiCallback::class.java)
    private val basicAuth = AuthInfo.ProvidedAuth("test-user-id", "test-password", false)
    private val tokenAuth = AuthInfo.TokenAuth("test-session-token")

    @Before
    fun setUp() {
        mockApiServer.start()
        mockApiServer.server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
    }

    @After
    fun tearDown() {
        mockApiServer.shutdown()
    }

    private fun authorizationHeader(expectedPath: String): String? {
        val request = mockApiServer.takeRequestOrFail()
        assertEquals(expectedPath, request.path)
        return request.getHeader("Authorization")
    }

    @Test
    fun `sendPhoneOtp with ProvidedAuth sends basic auth`() {
        ApiPersonalId.sendPhoneOtp(context, basicAuth, callback)
        val header = authorizationHeader(PersonalIdApiEndpoints.SEND_SESSION_OTP)
        assertTrue(header!!.startsWith("Basic "))
        assertEquals(HttpUtils.getCredential(basicAuth), header)
    }

    @Test
    fun `sendPhoneOtp with TokenAuth sends the session token credential`() {
        ApiPersonalId.sendPhoneOtp(context, tokenAuth, callback)
        assertEquals(
            HttpUtils.getCredential(tokenAuth),
            authorizationHeader(PersonalIdApiEndpoints.SEND_SESSION_OTP),
        )
    }

    @Test
    fun `validatePhoneOtp with ProvidedAuth sends basic auth`() {
        ApiPersonalId.validatePhoneOtp(context, basicAuth, "123456", callback)
        assertEquals(
            HttpUtils.getCredential(basicAuth),
            authorizationHeader(PersonalIdApiEndpoints.VALIDATE_SESSION_OTP),
        )
    }

    @Test
    fun `validatePhoneOtp with TokenAuth sends the session token credential`() {
        ApiPersonalId.validatePhoneOtp(context, tokenAuth, "123456", callback)
        assertEquals(
            HttpUtils.getCredential(tokenAuth),
            authorizationHeader(PersonalIdApiEndpoints.VALIDATE_SESSION_OTP),
        )
    }

    @Test
    fun `validateFirebaseIdToken with ProvidedAuth sends basic auth`() {
        ApiPersonalId.validateFirebaseIdToken(basicAuth, context, "firebase-id-token", callback)
        assertEquals(
            HttpUtils.getCredential(basicAuth),
            authorizationHeader(PersonalIdApiEndpoints.VALIDATE_FIREBASE_ID_TOKEN),
        )
    }

    @Test
    fun `validateFirebaseIdToken with TokenAuth sends the session token credential`() {
        ApiPersonalId.validateFirebaseIdToken(tokenAuth, context, "firebase-id-token", callback)
        assertEquals(
            HttpUtils.getCredential(tokenAuth),
            authorizationHeader(PersonalIdApiEndpoints.VALIDATE_FIREBASE_ID_TOKEN),
        )
    }
}
