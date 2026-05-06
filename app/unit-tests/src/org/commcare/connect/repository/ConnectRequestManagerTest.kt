package org.commcare.connect.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectRequestManagerTest {
    @After
    fun tearDown() {
        ConnectRequestManager.cancelAll()
    }

    @Test
    fun testSingleRequest_succeeds() =
        runBlocking {
            val url = "/test"
            var callCount = 0

            val result =
                ConnectRequestManager.executeRequest(url) {
                    callCount++
                    Result.success("data")
                }

            assertTrue(result.isSuccess)
            assertEquals("data", result.getOrNull())
            assertEquals(1, callCount)
        }

    @Test
    fun testSingleRequest_fails() =
        runBlocking {
            val url = "/test"
            val exception = Exception("Network error")

            val result =
                ConnectRequestManager.executeRequest(url) {
                    Result.failure<String>(exception)
                }

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun testDuplicateRequests_deduplicated() =
        runBlocking {
            val url = "/opportunities"
            var callCount = 0

            // Launch two concurrent requests for same URL
            val deferred1 =
                async {
                    ConnectRequestManager.executeRequest(url) {
                        callCount++
                        delay(100) // Simulate slow network
                        Result.success("data")
                    }
                }

            val deferred2 =
                async {
                    delay(10) // Start slightly later
                    ConnectRequestManager.executeRequest(url) {
                        callCount++
                        Result.success("data")
                    }
                }

            val result1 = deferred1.await()
            val result2 = deferred2.await()

            // Both succeed
            assertTrue(result1.isSuccess)
            assertTrue(result2.isSuccess)

            // But network was only called once
            assertEquals(1, callCount)
        }

    @Test
    fun testDifferentUrls_notDeduplicated() =
        runBlocking {
            var callCount = 0

            val deferred1 =
                async {
                    ConnectRequestManager.executeRequest("/url1") {
                        callCount++
                        Result.success("data1")
                    }
                }

            val deferred2 =
                async {
                    ConnectRequestManager.executeRequest("/url2") {
                        callCount++
                        Result.success("data2")
                    }
                }

            deferred1.await()
            deferred2.await()

            // Different URLs should both execute
            assertEquals(2, callCount)
        }

    @Test
    fun testIsRequestInProgress() =
        runBlocking {
            val url = "/test"

            assertFalse(ConnectRequestManager.isRequestInProgress(url))

            val deferred =
                async {
                    ConnectRequestManager.executeRequest(url) {
                        delay(100)
                        Result.success("data")
                    }
                }

            // Should be in progress
            delay(10)
            assertTrue(ConnectRequestManager.isRequestInProgress(url))

            // Wait for completion
            deferred.await()

            // Should no longer be in progress
            assertFalse(ConnectRequestManager.isRequestInProgress(url))
        }

    @Test
    fun testRequestContinuesAfterCallerCancellation() =
        runBlocking {
            // Verifies that the request lambda (network + DB write) runs to completion
            // even when the caller's coroutine is cancelled (simulating user navigation).
            val url = "/test"
            var requestCompleted = false

            val callerJob =
                async {
                    ConnectRequestManager.executeRequest(url) {
                        delay(100) // simulate network latency
                        requestCompleted = true
                        Result.success("data")
                    }
                }

            delay(10) // let the request start
            callerJob.cancel() // simulate user backing out (viewModelScope cancelled)

            // Give the app-scoped scope.launch time to complete the request
            delay(200)

            // The request lambda must have completed despite caller cancellation
            assertTrue(requestCompleted)
        }
}
