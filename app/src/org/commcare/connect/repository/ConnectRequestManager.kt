package org.commcare.connect.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.connect.network.personalId.LoginInvalidatedException
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages in-flight Connect API requests to prevent duplicate calls and ensure they complete even if the caller is cancelled.
 * Requests are deduplicated by [url] — concurrent calls with the same [url] share one in-flight request.
 * Requests are launched in an app-wide scope so they survive ViewModel cancellation (e.g. on back navigation).
 * Include DB writes inside the [request] block so they also complete even on back navigation.
 */
object ConnectRequestManager {
    @Volatile
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<Result<Any>>>()

    /**
     * Executes [request] in app scope — survives ViewModel cancellation.
     * Include DB writes inside [request] so they complete even on back navigation.
     * Duplicate calls to the same [url] share one in-flight request.
     */
    suspend fun <T> executeRequest(
        url: String,
        request: suspend () -> Result<T>,
    ): Result<T> {
        val deferred = CompletableDeferred<Result<Any>>()
        val existing = inFlightRequests.putIfAbsent(url, deferred)
        if (existing != null) {
            @Suppress("UNCHECKED_CAST")
            return existing.await() as Result<T>
        }

        // Launch in app scope so request + DB writes survive viewModelScope cancellation.
        // The entry is dropped before the deferred completes: an awaiting caller resumes as soon as
        // it completes, so leaving the entry in place until afterwards lets the next caller find a
        // finished request and reuse its result instead of going to the network.
        scope.launch {
            try {
                val result = request()
                inFlightRequests.remove(url, deferred)
                deferred.complete(result as Result<Any>)
            } catch (e: CancellationException) {
                inFlightRequests.remove(url, deferred)
                deferred.cancel(e)
                throw e
            } catch (e: Exception) {
                inFlightRequests.remove(url, deferred)
                deferred.completeExceptionally(e)
                throw e
            }
        }

        // deferred.await() IS cancellable — caller cancellation stops waiting but NOT the launch above.
        @Suppress("UNCHECKED_CAST")
        return deferred.await() as Result<T>
    }

    fun isRequestInProgress(url: String): Boolean = inFlightRequests.containsKey(url)

    // Call on logout to prevent stale data writes from previous-session requests.
    // Resets scope so the object can be reused after logout (important for tests and re-login).
    fun cancelAll() {
        inFlightRequests.values.forEach { it.cancel() }
        inFlightRequests.clear()
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
