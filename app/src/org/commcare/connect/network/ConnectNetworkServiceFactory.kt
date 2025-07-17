package org.commcare.connect.network

import okhttp3.OkHttpClient
import org.commcare.connect.PersonalIdManager
import org.commcare.core.network.AuthenticationInterceptor
import org.commcare.core.network.ModernHttpRequester
import org.commcare.network.HttpUtils
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ConnectNetworkServiceFactory {

    private const val CONNECT_ID_BASE_URL = "https://connectid.dimagi.com/"

    private val authInterceptor = AuthenticationInterceptor()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(ModernHttpRequester.CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(ModernHttpRequester.CONNECTION_SO_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        .addInterceptor(authInterceptor)

    private val connectIdRetrofit = Retrofit.Builder()
        .baseUrl(CONNECT_ID_BASE_URL)
        .client(httpClient.build())
        .build()

    fun createConnectIdNetworkSerive(): ConnectNetworkService {
        authInterceptor.setCredential(HttpUtils.getCredential(PersonalIdManager.getInstance().getConnectToken()))
        return connectIdRetrofit.create(ConnectNetworkService::class.java)
    }
}
