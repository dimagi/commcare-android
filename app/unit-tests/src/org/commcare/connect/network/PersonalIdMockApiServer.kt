package org.commcare.connect.network

import android.os.Handler
import android.os.Looper
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.connectId.PersonalIdApiClient
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Reusable [MockWebServer] harness that points [PersonalIdApiClient] at a local mock server so
 * PersonalID API calls hit it.
 *
 * [CallbackMode.MAIN_LOOPER] reproduces production threading for UI tests: Retrofit posts callbacks
 * to the main looper and [drainHttp] runs them deterministically. [CallbackMode.DIRECT] runs
 * callbacks inline on the OkHttp dispatcher thread for tests that don't touch views and simply
 * await a suspend call.
 */
class PersonalIdMockApiServer(
    private val callbackMode: CallbackMode = CallbackMode.MAIN_LOOPER,
) {
    enum class CallbackMode { MAIN_LOOPER, DIRECT }

    lateinit var server: MockWebServer
        private set
    private lateinit var httpDispatcher: Dispatcher

    @Volatile
    private var dispatchCallbacks = true

    fun start() {
        dispatchCallbacks = true
        server = MockWebServer()
        server.start()

        val retrofit =
            BaseApiClient
                .buildRetrofitClient(server.url("/").toString(), PersonalIdApiClient.API_VERSION)
                .newBuilder()
                .callbackExecutor { runnable ->
                    if (!dispatchCallbacks) return@callbackExecutor
                    when (callbackMode) {
                        CallbackMode.MAIN_LOOPER -> Handler(Looper.getMainLooper()).post(runnable)
                        CallbackMode.DIRECT -> runnable.run()
                    }
                }.build()
        httpDispatcher = (retrofit.callFactory() as OkHttpClient).dispatcher
        setPersonalIdApiService(retrofit.create(ApiService::class.java))
    }

    fun shutdown() {
        dispatchCallbacks = false
        setPersonalIdApiService(null)
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
     * the main looper, then drains UI work so the callback runs before assertions. Only meaningful
     * in [CallbackMode.MAIN_LOOPER].
     */
    fun drainHttp() {
        takeRequestOrFail()
        awaitHttpCallbackPosted()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }

    /**
     * Enqueues a primary-fetch response whose body is built from [items] and, when [acknowledges] is
     * true and [items] is non-empty, a follow-up acknowledge response — mirroring Connect API flows
     * where a non-empty fetch triggers a second round-trip. Pass `acknowledges = false` for flows
     * that fetch without an acknowledge step. [bodyBuilder] serializes the items to a response body.
     */
    fun <T> enqueueLoad(
        items: List<T>,
        bodyBuilder: (List<T>) -> String,
        acknowledges: Boolean = true,
    ) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(bodyBuilder(items)))
        if (acknowledges && items.isNotEmpty()) {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        }
    }

    /**
     * Drains the fetch (and the acknowledge round-trip, when [acknowledges] is true and [items] is
     * non-empty), then flushes UI work.
     */
    fun <T> drainLoad(
        items: List<T>,
        acknowledges: Boolean = true,
    ) {
        drainHttp()
        if (acknowledges && items.isNotEmpty()) {
            drainHttp()
        }
        ShadowLooper.idleMainLooper()
    }

    /** Convenience for [enqueueLoad] followed by [drainLoad]. */
    fun <T> completeLoad(
        items: List<T>,
        bodyBuilder: (List<T>) -> String,
        acknowledges: Boolean = true,
    ) {
        enqueueLoad(items, bodyBuilder, acknowledges)
        drainLoad(items, acknowledges)
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

    private fun setPersonalIdApiService(apiService: ApiService?) {
        val apiServiceField = PersonalIdApiClient::class.java.getDeclaredField("apiService")
        apiServiceField.isAccessible = true
        apiServiceField.set(null, apiService)
    }
}
