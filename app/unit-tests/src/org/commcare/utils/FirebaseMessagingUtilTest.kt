package org.commcare.utils

import org.junit.Assert
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ExecutionException

class FirebaseMessagingUtilTest {
    @Test
    fun testHasErrorInChain_directMatch() {
        val error: Throwable = IOException("FIS_AUTH_ERROR")
        Assert.assertTrue(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_wrappedInExecutionException() {
        val cause = IOException("FIS_AUTH_ERROR")
        val wrapped = ExecutionException(cause)
        Assert.assertTrue(FirebaseMessagingUtil.hasErrorInChain(wrapped, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_serviceNotAvailable() {
        val cause = IOException("SERVICE_NOT_AVAILABLE")
        val wrapped = ExecutionException(cause)
        Assert.assertTrue(FirebaseMessagingUtil.hasErrorInChain(wrapped, "SERVICE_NOT_AVAILABLE"))
    }

    @Test
    fun testHasErrorInChain_noMatch() {
        val error: Throwable = IOException("SOME_OTHER_ERROR")
        Assert.assertFalse(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_nullThrowable() {
        Assert.assertFalse(FirebaseMessagingUtil.hasErrorInChain(null, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_nullMessage() {
        val error = Throwable(null as String?)
        Assert.assertFalse(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_selfReferencingCause() {
        val error = SelfReferencingThrowable("FIS_AUTH_ERROR")
        // Should find the match on the first level before hitting the self-reference
        Assert.assertTrue(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_selfReferencingCauseNoMatch() {
        val error = SelfReferencingThrowable("SOME_OTHER_ERROR")
        // Should terminate without infinite loop despite self-referencing cause
        Assert.assertFalse(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_exceedsDepthLimit() {
        val depth = FirebaseMessagingUtil.MAX_CAUSE_CHAIN_DEPTH
        val deepest: Throwable = IOException("FIS_AUTH_ERROR")
        var current = deepest
        for (i in 0..<depth + 1) {
            current = Exception("wrapper " + i, current)
        }
        Assert.assertFalse(FirebaseMessagingUtil.hasErrorInChain(current, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_withinDepthLimit() {
        val depth = FirebaseMessagingUtil.MAX_CAUSE_CHAIN_DEPTH
        val deepest: Throwable = IOException("FIS_AUTH_ERROR")
        var current = deepest
        for (i in 0..<depth - 2) {
            current = Exception("wrapper " + i, current)
        }
        Assert.assertTrue(FirebaseMessagingUtil.hasErrorInChain(current, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_substringMatch() {
        val error: Throwable = IOException("Error: FIS_AUTH_ERROR occurred")
        Assert.assertTrue(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"))
    }

    @Test
    fun testHasErrorInChain_emptyErrorMessage() {
        // String.contains("") is always true, so this returns true for any non-null message
        val error: Throwable = IOException("anything")
        Assert.assertTrue(FirebaseMessagingUtil.hasErrorInChain(error, ""))
    }

    /**
     * A throwable whose getCause() returns itself, simulating a circular reference.
     */
    private class SelfReferencingThrowable(
        message: String?,
    ) : Throwable(message) {
        @get:Synchronized
        override val cause: Throwable
            get() = this
    }
}
