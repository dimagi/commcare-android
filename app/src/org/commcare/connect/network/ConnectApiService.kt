package org.commcare.connect.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.Path

interface ConnectApiService {
    @GET("/api/opportunity/")
    suspend fun getConnectOpportunities(
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>

    @GET("/api/opportunity/{id}/learn_progress")
    suspend fun getLearningProgress(
        @Header("Authorization") authorization: String,
        @Path("id") jobId: String,
        @HeaderMap headers: Map<String, String>,
    ): Response<ResponseBody>
}
