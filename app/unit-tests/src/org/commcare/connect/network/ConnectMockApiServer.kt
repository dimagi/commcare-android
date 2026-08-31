package org.commcare.connect.network

import android.os.Handler
import android.os.Looper
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.connect.ConnectApiService
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.commcare.connect.repository.ConnectRepository
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * [MockWebServer] harness that points [ConnectNetworkClient] at a local mock server so Connect API
 * calls hit it. The PersonalID equivalent lives in `PersonalIdMockApiServer`; the two target
 * different Retrofit clients.
 * [ConnectNetworkClient] is a process-wide singleton, so [start] swaps its backing instance and
 * [shutdown] must restore it. Callers seat a Connect user with a valid token (see `ConnectTestUtils`)
 * so the calls don't detour to the PersonalId token endpoint, which this server doesn't serve.
 *
 * Retrofit posts callbacks to the main looper as it does in production, and [drainHttp] runs them
 * deterministically. Calls that resume on a background dispatcher and only then post their result to
 * the main looper need [awaitRequest] instead.
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
        setNetworkClient(ConnectNetworkClient(retrofit.create(ConnectApiService::class.java)))
        ConnectRepository.resetInstance()
    }

    fun shutdown() {
        dispatchCallbacks = false
        setNetworkClient(null)
        ConnectRepository.resetInstance()
        server.shutdown()
    }

    fun enqueueJson(
        body: String,
        code: Int = 200,
    ) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    fun enqueueError(code: Int) = server.enqueue(MockResponse().setResponseCode(code).setBody("{}"))

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

    /**
     * Waits for the next request to reach the server, then drains the main looper so the callback
     * the API call posts there has run before assertions.
     */
    fun awaitRequest(timeoutSeconds: Long = 10): RecordedRequest {
        val request = takeRequestOrFail(timeoutSeconds)
        idleUntilQuiet()
        return request
    }

    /** Asserts no request reached the server. Costs [timeoutSeconds] of wall clock, so keep it short. */
    fun assertNoRequest(timeoutSeconds: Long = 1) {
        idleUntilQuiet()
        val request = server.takeRequest(timeoutSeconds, TimeUnit.SECONDS)
        if (request != null) {
            throw AssertionError("Expected no Connect API request, but got ${request.path}")
        }
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

    /**
     * The callback hops from a background thread onto the main looper, so a single idle can run
     * before the post lands. Idling repeatedly with a short pause covers that handoff.
     */
    private fun idleUntilQuiet() {
        repeat(HANDOFF_IDLE_ROUNDS) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(HANDOFF_PAUSE_MS)
        }
        ShadowLooper.idleMainLooper()
    }

    /** The companion's backing field is a static on [ConnectNetworkClient] itself, and is private. */
    private fun setNetworkClient(client: ConnectNetworkClient?) {
        ConnectNetworkClient::class.java
            .getDeclaredField("instance")
            .apply { isAccessible = true }
            .set(null, client)
    }

    companion object {
        private const val HANDOFF_IDLE_ROUNDS = 20
        private const val HANDOFF_PAUSE_MS = 25L
    }
}
