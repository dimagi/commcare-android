package org.commcare.connect.network

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface ConnectNetworkService {

    @POST("users/heartbeat")
    fun makeHeartbeatRequest(
        @Body body: HeartBeatBody
    ): Call<ResponseBody?>?
}
