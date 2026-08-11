package org.commcare.connect.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.commcare.connect.network.base.BaseApiClient
import org.commcare.connect.network.connect.ConnectNetworkClient
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * Reusable [MockWebServer] harness that points [ConnectNetworkClient] at a local mock server, so the
 * Connect API calls — the request, the Retrofit plumbing, and the response parsers — all run for
 * real against enqueued responses.
 *
 * The client is a process-wide singleton, so [start] swaps its backing instance and [shutdown] must
 * restore it. Callers seat a Connect user with a valid token (see `ConnectTestUtils`) so the calls
 * don't detour to the PersonalId token endpoint, which this server doesn't serve.
 *
 * Progress calls run on `Dispatchers.IO` and post their result back to the main looper, hence
 * [awaitRequest] (a real background thread has to reach the server) followed by an idle of the main
 * looper.
 */
class ConnectMockApiServer {
    lateinit var server: MockWebServer
        private set

    fun start() {
        server = MockWebServer()
        server.start()
        setNetworkClient(
            ConnectNetworkClient(
                BaseApiClient
                    .buildRetrofitClient(server.url("/").toString())
                    .create(ConnectApiService::class.java),
            ),
        )
    }

    fun shutdown() {
        setNetworkClient(null)
        server.shutdown()
    }

    fun enqueueJson(
        body: String,
        code: Int = 200,
    ) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    fun enqueueError(code: Int) = server.enqueue(MockResponse().setResponseCode(code).setBody("{}"))

    /**
     * Waits for the next request to reach the server, then drains the main looper so the callback
     * the API call posts there has run before assertions.
     */
    fun awaitRequest(timeoutSeconds: Long = 10): RecordedRequest {
        val request =
            server.takeRequest(timeoutSeconds, TimeUnit.SECONDS)
                ?: throw AssertionError("Expected a Connect API request within ${timeoutSeconds}s but none arrived")
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

    /**
     * The callback hops from an IO thread onto the main looper, so a single idle can run before the
     * post lands. Idling repeatedly with a short pause covers that handoff.
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
