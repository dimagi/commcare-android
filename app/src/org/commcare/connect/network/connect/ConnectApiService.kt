package org.commcare.connect.network.connect

import okhttp3.ResponseBody
import org.commcare.connect.network.connect.models.ConfirmPaymentsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path

interface ConnectApiService {
    @GET(ConnectApiEndpoints.connectOpportunitiesURL)
    suspend fun getConnectOpportunities(
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>

    @GET(ConnectApiEndpoints.connectLearnProgressURL)
    suspend fun getLearningProgress(
        @Header("Authorization") authorization: String,
        @Path("id") jobId: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>

    @GET(ConnectApiEndpoints.connectDeliveriesURL)
    suspend fun getDeliveryProgress(
        @Header("Authorization") authorization: String,
        @Path("id") jobId: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST(ConnectApiEndpoints.connectStartLearningURL)
    suspend fun startLearnApp(
        @Header("Authorization") auth: String,
        @HeaderMap headers: Map<String, String>,
        @Field("opportunity") opportunityId: String,
    ): Response<ResponseBody>

    @POST(ConnectApiEndpoints.connectClaimJobURL)
    suspend fun claimJob(
        @Header("Authorization") auth: String,
        @Path("id") jobUUID: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: Map<String, String>,
    ): Response<ResponseBody>

    @POST(ConnectApiEndpoints.PAYMENT_CONFIRMAITONS)
    suspend fun confirmPayments(
        @Header("Authorization") auth: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: ConfirmPaymentsRequest,
    ): Response<ResponseBody>
}
