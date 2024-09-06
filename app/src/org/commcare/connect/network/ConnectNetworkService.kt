package org.commcare.connect.network

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ConnectNetworkService {

    @POST("users/heartbeat")
    fun makeHeartbeatRequest(
        @Body body: HeartBeatBody
    ): Call<ResponseBody?>?
}
