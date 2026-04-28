package org.commcare.connect.network.connectId.parser

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.commcare.CommCareApp
import org.commcare.CommCareApplication
import org.commcare.CommCareTestApplication
import org.commcare.connect.ConnectConstants
import org.commcare.connect.database.ConnectDatabaseHelper
import org.commcare.connect.network.SsoToken
import org.commcare.core.network.AuthInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.eq
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.Date
import kotlin.math.abs

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RetrieveHqTokenResponseParserTest {
    companion object {
        private const val TEST_HQ_USERNAME = "test-hq-user@example.com"
        private const val TEST_APP_ID = "test-app-unique-id"
        private const val TEST_TOKEN = "hq-access-token-abc123"
        private const val EXPIRATION_TOLERANCE_MS = 5000L
    }

    private val context: Context = CommCareTestApplication.instance()
    private lateinit var parser: RetrieveHqTokenResponseParser<AuthInfo.TokenAuth>

    @Before
    fun setUp() {
        mockkStatic(CommCareApplication::class)
        val mockCCApp = mockk<CommCareApplication>()
        every { CommCareApplication.instance() } returns mockCCApp
        val mockCurrentApp = mockk<CommCareApp>()
        every { mockCCApp.getCurrentApp() } returns mockCurrentApp
        every { mockCurrentApp.getUniqueId() } returns TEST_APP_ID

        parser = RetrieveHqTokenResponseParser(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(CommCareApplication::class)
    }

    private fun createTokenJson(
        token: String,
        expiresIn: Int? = null,
    ): String {
        val expiresField =
            if (expiresIn != null) {
                """, "${ConnectConstants.CONNECT_KEY_EXPIRES}": $expiresIn"""
            } else {
                ""
            }
        return """{"${ConnectConstants.CONNECT_KEY_TOKEN}": "$token"$expiresField}"""
    }

    @Test
    fun testParse_validTokenWithExpires_returnsTokenAuth() {
        mockStatic(ConnectDatabaseHelper::class.java).use {
            val inputStream = ByteArrayInputStream(createTokenJson(TEST_TOKEN, 3600).toByteArray())
            val result = parser.parse(200, inputStream, TEST_HQ_USERNAME)
            assertEquals(TEST_TOKEN, result.bearerToken)
        }
    }

    @Test
    fun testParse_validTokenWithExpires_storesCorrectTokenAndExpiration() {
        mockStatic(ConnectDatabaseHelper::class.java).use { mockedDb ->
            val currentTime = Date()
            val expiresIn = 3600
            val inputStream = ByteArrayInputStream(createTokenJson(TEST_TOKEN, expiresIn).toByteArray())
            parser.parse(200, inputStream, TEST_HQ_USERNAME)

            val ssoTokenCaptor = ArgumentCaptor.forClass(SsoToken::class.java)
            mockedDb.verify(
                {
                    ConnectDatabaseHelper.storeHqToken(
                        eq(context),
                        eq(TEST_APP_ID),
                        eq(TEST_HQ_USERNAME),
                        ssoTokenCaptor.capture(),
                    )
                },
                times(1),
            )
            val capturedToken = ssoTokenCaptor.value
            assertEquals(TEST_TOKEN, capturedToken.token)
            val expectedExpiration = currentTime.time + (expiresIn.toLong() * 1000)
            assertTrue(abs(capturedToken.expiration.time - expectedExpiration) < EXPIRATION_TOLERANCE_MS)
        }
    }

    @Test
    fun testParse_tokenWithoutExpiresField_expirationApproximatelyNow() {
        mockStatic(ConnectDatabaseHelper::class.java).use { mockedDb ->
            val currentTime = Date()
            val inputStream = ByteArrayInputStream(createTokenJson(TEST_TOKEN).toByteArray())
            parser.parse(200, inputStream, TEST_HQ_USERNAME)

            val ssoTokenCaptor = ArgumentCaptor.forClass(SsoToken::class.java)
            mockedDb.verify(
                {
                    ConnectDatabaseHelper.storeHqToken(
                        eq(context),
                        eq(TEST_APP_ID),
                        eq(TEST_HQ_USERNAME),
                        ssoTokenCaptor.capture(),
                    )
                },
                times(1),
            )
            val capturedToken = ssoTokenCaptor.value
            assertEquals(TEST_TOKEN, capturedToken.token)
            assertTrue(abs(capturedToken.expiration.time - currentTime.time) < EXPIRATION_TOLERANCE_MS)
        }
    }

    @Test
    fun testParse_zeroExpiresField_expirationApproximatelyNow() {
        mockStatic(ConnectDatabaseHelper::class.java).use { mockedDb ->
            val currentTime = Date()
            val inputStream = ByteArrayInputStream(createTokenJson(TEST_TOKEN, 0).toByteArray())
            parser.parse(200, inputStream, TEST_HQ_USERNAME)

            val ssoTokenCaptor = ArgumentCaptor.forClass(SsoToken::class.java)
            mockedDb.verify(
                {
                    ConnectDatabaseHelper.storeHqToken(
                        eq(context),
                        eq(TEST_APP_ID),
                        eq(TEST_HQ_USERNAME),
                        ssoTokenCaptor.capture(),
                    )
                },
                times(1),
            )
            val capturedToken = ssoTokenCaptor.value
            assertEquals(TEST_TOKEN, capturedToken.token)
            assertTrue(abs(capturedToken.expiration.time - currentTime.time) < EXPIRATION_TOLERANCE_MS)
        }
    }

    @Test(expected = RuntimeException::class)
    fun testParse_invalidJson_throwsRuntimeException() {
        mockStatic(ConnectDatabaseHelper::class.java).use {
            val inputStream = ByteArrayInputStream("{ invalid json }".toByteArray())
            parser.parse(200, inputStream, TEST_HQ_USERNAME)
        }
    }

    @Test(expected = RuntimeException::class)
    fun testParse_emptyResponse_throwsRuntimeException() {
        mockStatic(ConnectDatabaseHelper::class.java).use {
            val inputStream = ByteArrayInputStream("".toByteArray())
            parser.parse(200, inputStream, TEST_HQ_USERNAME)
        }
    }

    @Test(expected = RuntimeException::class)
    fun testParse_missingTokenField_throwsRuntimeException() {
        mockStatic(ConnectDatabaseHelper::class.java).use {
            val jsonWithoutToken = """{"${ConnectConstants.CONNECT_KEY_EXPIRES}": 3600}"""
            val inputStream = ByteArrayInputStream(jsonWithoutToken.toByteArray())
            parser.parse(200, inputStream, TEST_HQ_USERNAME)
        }
    }
}
