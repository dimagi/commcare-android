package org.commcare.connect.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.connect.network.LoginInvalidatedException
import java.util.concurrent.ConcurrentHashMap

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
        scope.launch {
            try {
                val result = request()
                deferred.complete(result as Result<Any>)
            } catch (e: CancellationException) {
                deferred.cancel(e)
                throw e
            } catch (e: LoginInvalidatedException) {
                throw e
            } catch (e: Exception) {
                deferred.complete(Result.failure<Any>(e))
            } finally {
                inFlightRequests.remove(url)
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
