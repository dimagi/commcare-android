package org.commcare.connect.network.connect

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.ConnectApiService
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.connect.network.getAuthorizationHeader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

@Config(application = CommCareTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ConnectNetworkClientTest {
    private val context = ApplicationProvider.getApplicationContext<CommCareTestApplication>()
    private val mockApiService = mockk<ConnectApiService>()
    private val mockUser = mockk<ConnectUserRecord>()
    private lateinit var client: ConnectNetworkClient

    @Before
    fun setUp() {
        client = ConnectNetworkClient(context, mockApiService)
        mockkStatic("org.commcare.connect.network.ConnectNetworkHelperKt")
        coEvery { getAuthorizationHeader(any(), any()) } returns Result.success("Bearer testtoken")
    }

    @After
    fun tearDown() {
        unmockkStatic("org.commcare.connect.network.ConnectNetworkHelperKt")
    }

    @Test
    fun testGetConnectOpportunities_authHeaderFailure_returnsFailure() =
        runBlocking {
            coEvery { getAuthorizationHeader(any(), any()) } returns
                Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR))

            val result = client.getConnectOpportunities(mockUser)

            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }

    @Test
    fun testGetConnectOpportunities_httpError401_returnsFailedAuth() =
        runBlocking {
            val errorBody = "".toResponseBody("application/json".toMediaType())
            val mockResponse = Response.error<ResponseBody>(401, errorBody)
            coEvery { mockApiService.getConnectOpportunities(any(), any()) } returns mockResponse

            val result = client.getConnectOpportunities(mockUser)

            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }

    @Test
    fun testGetConnectOpportunities_networkException_returnsNetworkError() =
        runBlocking {
            coEvery { mockApiService.getConnectOpportunities(any(), any()) } throws
                IOException("Network failed")

            val result = client.getConnectOpportunities(mockUser)

            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }

    @Test
    fun testGetConnectOpportunities_http500_returnsServerError() =
        runBlocking {
            val errorBody = "".toResponseBody("application/json".toMediaType())
            val mockResponse = Response.error<ResponseBody>(500, errorBody)
            coEvery { mockApiService.getConnectOpportunities(any(), any()) } returns mockResponse

            val result = client.getConnectOpportunities(mockUser)

            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }

    @Test
    fun testGetLearningProgress_authHeaderFailure_returnsFailure() =
        runBlocking {
            val mockJob = mockk<ConnectJobRecord>()
            every { mockJob.jobUUID } returns "test-uuid"
            coEvery { getAuthorizationHeader(any(), any()) } returns
                Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR))

            val result = client.getLearningProgress(mockUser, mockJob)

            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }

    @Test
    fun testGetLearningProgress_http500_returnsServerError() =
        runBlocking {
            val mockJob = mockk<ConnectJobRecord>()
            every { mockJob.jobUUID } returns "test-uuid"
            val errorBody = "".toResponseBody("application/json".toMediaType())
            val mockResponse = Response.error<ResponseBody>(500, errorBody)
            coEvery { mockApiService.getLearningProgress(any(), any(), any()) } returns mockResponse

            val result = client.getLearningProgress(mockUser, mockJob)

            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }
}
