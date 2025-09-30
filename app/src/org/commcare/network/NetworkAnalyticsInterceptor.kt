package org.commcare.network

import okhttp3.Interceptor
import okhttp3.Response
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil
import org.javarosa.core.services.Logger

/**
 * OkHttp interceptor that logs analytics events for all HTTP requests/responses
 */
class NetworkAnalyticsInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        var response: Response?
        var responseCode = -1
        
        try {
            response = chain.proceed(request)
            responseCode = response.code
            return response
        } finally {
            val duration = System.currentTimeMillis() - startTime
            logNetworkAnalytics(request.url.toString(), responseCode, duration)
        }
    }

    private fun logNetworkAnalytics(
        url: String,
        responseCode: Int, 
        duration: Long,
    ) {
        try {
            FirebaseAnalyticsUtil.reportNetworkRequest(url, responseCode, duration)
        } catch (e: Exception) {
            Logger.exception("Error logging network analytics", e)
        }
    }
}
