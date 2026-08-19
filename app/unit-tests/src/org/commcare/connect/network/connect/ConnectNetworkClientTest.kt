package org.commcare.connect.network.connect

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.commcare.CommCareTestApplication
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.android.database.connect.models.ConnectUserRecord
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.base.ConnectApiException
import org.commcare.connect.network.connect.ConnectApiService
import org.commcare.connect.network.connect.models.ConfirmPaymentsRequest
import org.commcare.connect.network.connect.models.ConnectPaymentConfirmationModel
import org.commcare.connect.network.connect.models.PaymentConfirmationBody
import org.commcare.connect.network.connectId.ConnectSsoSyncHelper
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
    private val mockApiService = mockk<ConnectApiService>()
    private val mockUser = mockk<ConnectUserRecord>()
    private lateinit var client: ConnectNetworkClient

    @Before
    fun setUp() {
        client = ConnectNetworkClient(mockApiService)
        mockkObject(ConnectSsoSyncHelper)
        coEvery { ConnectSsoSyncHelper.getAuthorizationHeader(any()) } returns Result.success("Bearer testtoken")
    }

    @After
    fun tearDown() {
        unmockkObject(ConnectSsoSyncHelper)
    }

    @Test
    fun testGetConnectOpportunities_authHeaderFailure_returnsFailure() =
        runBlocking {
            coEvery { ConnectSsoSyncHelper.getAuthorizationHeader(any()) } returns
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
            coEvery { ConnectSsoSyncHelper.getAuthorizationHeader(any()) } returns
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

    @Test
    fun testGetConnectOpportunities_success_returnsOpportunities() =
        runBlocking {
            val responseBody = "".toResponseBody("application/json".toMediaType())
            coEvery { mockApiService.getConnectOpportunities(any(), any()) } returns Response.success(responseBody)
            val result = client.getConnectOpportunities(mockUser)
            assertTrue(result.isSuccess)
        }

    @Test
    fun testGetLearningProgress_success_returnsLearnProgress() =
        runBlocking {
            val mockJob = mockk<ConnectJobRecord>()
            every { mockJob.jobUUID } returns "test-uuid"
            val responseBody = "".toResponseBody("application/json".toMediaType())
            coEvery { mockApiService.getLearningProgress(any(), any(), any()) } returns
                Response.success(
                    responseBody,
                )
            val result = client.getLearningProgress(mockUser, mockJob)
            assertTrue(result.isSuccess)
        }

    @Test
    fun testGetDeliveryProgress_authHeaderFailure_returnsFailure() =
        runBlocking {
            val mockJob = mockk<ConnectJobRecord>()
            every { mockJob.jobUUID } returns "test-uuid"
            coEvery { ConnectSsoSyncHelper.getAuthorizationHeader(any()) } returns
                Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR))

            val result = client.getDeliveryProgress(mockUser, mockJob)

            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }

    @Test
    fun testGetDeliveryProgress_http500_returnsServerError() =
        runBlocking {
            val mockJob = mockk<ConnectJobRecord>()
            every { mockJob.jobUUID } returns "test-uuid"
            val errorBody = "".toResponseBody("application/json".toMediaType())
            val mockResponse = Response.error<ResponseBody>(500, errorBody)
            coEvery { mockApiService.getDeliveryProgress(any(), any(), any()) } returns mockResponse

            val result = client.getDeliveryProgress(mockUser, mockJob)

            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }

    @Test
    fun testGetDeliveryProgress_success_returnsDeliveryProgress() =
        runBlocking {
            val mockJob = mockk<ConnectJobRecord>()
            every { mockJob.jobUUID } returns "test-uuid"
            val responseBody = "".toResponseBody("application/json".toMediaType())
            coEvery { mockApiService.getDeliveryProgress(any(), any(), any()) } returns
                Response.success(
                    responseBody,
                )
            val result = client.getDeliveryProgress(mockUser, mockJob)
            assertTrue(result.isSuccess)
        }

    @Test
    fun testStartLearnApp_success_returnsSuccess() =
        runBlocking {
            val opportunityIdSlot = slot<String>()
            coEvery { mockApiService.startLearnApp(any(), any(), capture(opportunityIdSlot)) } returns
                Response.success("".toResponseBody("application/json".toMediaType()))
            val result = client.startLearnApp(mockUser, "test-uuid")
            assertTrue(result.isSuccess)
            assertEquals("test-uuid", opportunityIdSlot.captured)
        }

    @Test
    fun testStartLearnApp_authHeaderFailure_returnsFailure() =
        runBlocking {
            coEvery { ConnectSsoSyncHelper.getAuthorizationHeader(any()) } returns
                Result.failure(ConnectApiException(PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR))
            val result = client.startLearnApp(mockUser, "test-uuid")
            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.TOKEN_UNAVAILABLE_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
        }

    @Test
    fun testStartLearnApp_http401_returnsFailedAuth() =
        runBlocking {
            val opportunityIdSlot = slot<String>()
            val errorBody = "".toResponseBody("application/json".toMediaType())
            coEvery { mockApiService.startLearnApp(any(), any(), capture(opportunityIdSlot)) } returns Response.error(401, errorBody)
            val result = client.startLearnApp(mockUser, "test-uuid")
            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
            assertEquals("test-uuid", opportunityIdSlot.captured)
        }

    @Test
    fun testClaimJob_success_returnsSuccess() =
        runBlocking {
            val jobUuidSlot = slot<String>()
            val bodySlot = slot<Map<String, String>>()
            coEvery { mockApiService.claimJob(any(), capture(jobUuidSlot), any(), capture(bodySlot)) } returns
                Response.success("".toResponseBody("application/json".toMediaType()))
            val result = client.claimJob(mockUser, "test-uuid")
            assertTrue(result.isSuccess)
            assertEquals("test-uuid", jobUuidSlot.captured)
            assertTrue(bodySlot.captured.isEmpty())
        }

    @Test
    fun testClaimJob_http403_returnsForbidden() =
        runBlocking {
            val jobUuidSlot = slot<String>()
            val bodySlot = slot<Map<String, String>>()
            val errorBody = "".toResponseBody("application/json".toMediaType())
            coEvery { mockApiService.claimJob(any(), capture(jobUuidSlot), any(), capture(bodySlot)) } returns
                Response.error(403, errorBody)
            val result = client.claimJob(mockUser, "test-uuid")
            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
            assertEquals("test-uuid", jobUuidSlot.captured)
            assertTrue(bodySlot.captured.isEmpty())
        }

    @Test
    fun testClaimJob_networkException_returnsNetworkError() =
        runBlocking {
            val jobUuidSlot = slot<String>()
            val bodySlot = slot<Map<String, String>>()
            coEvery { mockApiService.claimJob(any(), capture(jobUuidSlot), any(), capture(bodySlot)) } throws IOException("timeout")
            val result = client.claimJob(mockUser, "test-uuid")
            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
            assertEquals("test-uuid", jobUuidSlot.captured)
            assertTrue(bodySlot.captured.isEmpty())
        }

    @Test
    fun testConfirmPayments_success_returnsSuccess() =
        runBlocking {
            val requestSlot = slot<ConfirmPaymentsRequest>()
            coEvery { mockApiService.confirmPayments(any(), any(), capture(requestSlot)) } returns
                Response.success("".toResponseBody("application/json".toMediaType()))
            val mockPayment1 = mockk<ConnectJobPaymentRecord>()
            every { mockPayment1.paymentUUID } returns "pay-1"
            val mockPayment2 = mockk<ConnectJobPaymentRecord>()
            every { mockPayment2.paymentUUID } returns "pay-2"
            val confirmations =
                listOf(
                    ConnectPaymentConfirmationModel(mockPayment1, true),
                    ConnectPaymentConfirmationModel(mockPayment2, false),
                )
            val result = client.confirmPayments(mockUser, confirmations)
            assertTrue(result.isSuccess)
            assertEquals(2, requestSlot.captured.payments.size)
            assertEquals(PaymentConfirmationBody("pay-1", "true"), requestSlot.captured.payments[0])
            assertEquals(PaymentConfirmationBody("pay-2", "false"), requestSlot.captured.payments[1])
        }

    @Test
    fun testConfirmPayments_http500_returnsServerError() =
        runBlocking {
            val requestSlot = slot<ConfirmPaymentsRequest>()
            val errorBody = "".toResponseBody("application/json".toMediaType())
            coEvery { mockApiService.confirmPayments(any(), any(), capture(requestSlot)) } returns Response.error(500, errorBody)
            val mockPayment1 = mockk<ConnectJobPaymentRecord>()
            every { mockPayment1.paymentUUID } returns "pay-1"
            val mockPayment2 = mockk<ConnectJobPaymentRecord>()
            every { mockPayment2.paymentUUID } returns "pay-2"
            val confirmations =
                listOf(
                    ConnectPaymentConfirmationModel(mockPayment1, true),
                    ConnectPaymentConfirmationModel(mockPayment2, false),
                )
            val result = client.confirmPayments(mockUser, confirmations)
            assertTrue(result.isFailure)
            assertEquals(
                PersonalIdOrConnectApiErrorCodes.SERVER_ERROR,
                (result.exceptionOrNull() as ConnectApiException).errorCode,
            )
            assertEquals(2, requestSlot.captured.payments.size)
            assertEquals(PaymentConfirmationBody("pay-1", "true"), requestSlot.captured.payments[0])
            assertEquals(PaymentConfirmationBody("pay-2", "false"), requestSlot.captured.payments[1])
        }
}
