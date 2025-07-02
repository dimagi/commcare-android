package org.commcare.connect.network.connect

import org.commcare.connect.network.base.BaseApiClient.buildRetrofitClient
import org.commcare.connect.network.ApiService
import org.commcare.dalvik.BuildConfig

/**
 * Retrofit client for Connect API
 */
class ConnectApiClient {

    companion object {

        val BASE_URL: String = "https://${BuildConfig.CCC_HOST}"
        private var apiService: ApiService? = null

        fun getClientApi(): ApiService {
            if (apiService == null) {
                synchronized(ConnectApiClient::class.java) { // Double-checked locking
                    if (apiService == null) {
                        apiService = buildRetrofitClient(BASE_URL).create(ApiService::class.java)
                    }
                }
            }
            return apiService!!
        }
    }


}