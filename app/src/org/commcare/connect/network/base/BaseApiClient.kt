package org.commcare.connect.network.base

import android.text.TextUtils
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.commcare.dalvik.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Base class for all API retrofit clients
 */
object BaseApiClient {

    fun buildRetrofitClient(baseUrl: String, apiVersion: String? = null): Retrofit {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE)
        logging.redactHeader("Authorization")
        logging.redactHeader("Cookie")

        val okHttpClient: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(Interceptor { chain: Interceptor.Chain ->
                val originalRequest = chain.request()
                val requestWithHeadersBuilder = originalRequest.newBuilder()
                if (!TextUtils.isEmpty(apiVersion)) {
                    requestWithHeadersBuilder.header(
                        "Accept",
                        "application/json;version=" + apiVersion
                    )
                }
                chain.proceed(requestWithHeadersBuilder.build())
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}