package org.commcare.connect.network

import android.os.Handler
import android.os.Looper
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.connect.ConnectApiClient
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * [MockWebServer] harness that points [ConnectApiClient] at a local mock server so Connect API
 * calls hit it. The PersonalID equivalent lives in `PersonalIdMockApiServer`; the two target
 * different Retrofit clients.
 *
 * Retrofit posts callbacks to the main looper as it does in production, and [drainHttp] runs them
 * deterministically.
 */
class ConnectMockApiServer {
    lateinit var server: MockWebServer
        private set
    private lateinit var httpDispatcher: Dispatcher

    @Volatile
    private var dispatchCallbacks = true

    val requestCount: Int get() = server.requestCount

    fun start() {
        dispatchCallbacks = true
        server = MockWebServer()
        server.start()

        val retrofit =
            BaseApiClient
                .buildRetrofitClient(server.url("/").toString())
                .newBuilder()
                .callbackExecutor { runnable ->
                    if (!dispatchCallbacks) return@callbackExecutor
                    Handler(Looper.getMainLooper()).post(runnable)
                }.build()
        httpDispatcher = (retrofit.callFactory() as OkHttpClient).dispatcher
        setConnectApiService(retrofit.create(ApiService::class.java))
    }

    fun shutdown() {
        dispatchCallbacks = false
        setConnectApiService(null)
        server.shutdown()
    }

    /**
     * Reads the next request with a bounded wait so a missing dispatch fails fast instead of
     * hanging the suite.
     */
    fun takeRequestOrFail(timeoutSeconds: Long = 5): RecordedRequest =
        server.takeRequest(timeoutSeconds, TimeUnit.SECONDS)
            ?: throw AssertionError("Expected an HTTP request within ${timeoutSeconds}s but none arrived")

    /**
     * Waits for the next request to reach the mock server and its response callback to be posted to
     * the main looper, then drains UI work so the callback runs before assertions. Returns the
     * request so callers can assert on it.
     */
    fun drainHttp(): RecordedRequest {
        val request = takeRequestOrFail()
        awaitHttpCallbackPosted()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        return request
    }

    private fun awaitHttpCallbackPosted(timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (httpDispatcher.runningCallsCount() > 0) {
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("HTTP call did not complete within ${timeoutMs}ms")
            }
            Thread.sleep(10)
        }
    }

    private fun setConnectApiService(apiService: ApiService?) {
        val apiServiceField = ConnectApiClient::class.java.getDeclaredField("apiService")
        apiServiceField.isAccessible = true
        apiServiceField.set(null, apiService)
    }
}
