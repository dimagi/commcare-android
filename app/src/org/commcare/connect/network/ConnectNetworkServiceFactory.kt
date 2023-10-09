package org.commcare.connect.network

import okhttp3.OkHttpClient
import org.commcare.activities.connect.ConnectManager
import org.commcare.core.network.AuthenticationInterceptor
import org.commcare.core.network.ModernHttpRequester
import org.commcare.network.HttpUtils
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ConnectNetworkServiceFactory {

    private const val CONNECT_BASE_URL = "https://connect.dimagi.com/"

    private val auth = HttpUtils.getCredential(ConnectManager.getConnectToken())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(ModernHttpRequester.CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(ModernHttpRequester.CONNECTION_SO_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .addInterceptor(AuthenticationInterceptor(auth))

    private val retrofit = Retrofit.Builder()
        .baseUrl(CONNECT_BASE_URL)
        .client(httpClient.build())
        .build()

    fun createConnectNetworkSerive(): ConnectNetworkService {
        return retrofit.create(ConnectNetworkService::class.java)
    }
}
