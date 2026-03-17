package org.commcare.utils;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FirebaseMessagingUtilTest {

    @Test
    public void testHasErrorInChain_directMatch() {
        Throwable error = new IOException("FIS_AUTH_ERROR");
        assertTrue(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_wrappedInExecutionException() {
        IOException cause = new IOException("FIS_AUTH_ERROR");
        ExecutionException wrapped = new ExecutionException(cause);
        assertTrue(FirebaseMessagingUtil.hasErrorInChain(wrapped, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_serviceNotAvailable() {
        IOException cause = new IOException("SERVICE_NOT_AVAILABLE");
        ExecutionException wrapped = new ExecutionException(cause);
        assertTrue(FirebaseMessagingUtil.hasErrorInChain(wrapped, "SERVICE_NOT_AVAILABLE"));
    }

    @Test
    public void testHasErrorInChain_noMatch() {
        Throwable error = new IOException("SOME_OTHER_ERROR");
        assertFalse(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_nullThrowable() {
        assertFalse(FirebaseMessagingUtil.hasErrorInChain(null, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_nullMessage() {
        Throwable error = new Throwable((String)null);
        assertFalse(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_selfReferencingCause() {
        SelfReferencingThrowable error = new SelfReferencingThrowable("FIS_AUTH_ERROR");
        // Should find the match on the first level before hitting the self-reference
        assertTrue(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_selfReferencingCauseNoMatch() {
        SelfReferencingThrowable error = new SelfReferencingThrowable("SOME_OTHER_ERROR");
        // Should terminate without infinite loop despite self-referencing cause
        assertFalse(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_exceedsDepthLimit() {
        int depth = FirebaseMessagingUtil.MAX_CAUSE_CHAIN_DEPTH;
        Throwable deepest = new IOException("FIS_AUTH_ERROR");
        Throwable current = deepest;
        for (int i = 0; i < depth + 1; i++) {
            current = new Exception("wrapper " + i, current);
        }
        assertFalse(FirebaseMessagingUtil.hasErrorInChain(current, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_withinDepthLimit() {
        int depth = FirebaseMessagingUtil.MAX_CAUSE_CHAIN_DEPTH;
        Throwable deepest = new IOException("FIS_AUTH_ERROR");
        Throwable current = deepest;
        for (int i = 0; i < depth - 2; i++) {
            current = new Exception("wrapper " + i, current);
        }
        assertTrue(FirebaseMessagingUtil.hasErrorInChain(current, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_substringMatch() {
        Throwable error = new IOException("Error: FIS_AUTH_ERROR occurred");
        assertTrue(FirebaseMessagingUtil.hasErrorInChain(error, "FIS_AUTH_ERROR"));
    }

    @Test
    public void testHasErrorInChain_emptyErrorMessage() {
        // String.contains("") is always true, so this returns true for any non-null message
        Throwable error = new IOException("anything");
        assertTrue(FirebaseMessagingUtil.hasErrorInChain(error, ""));
    }

    /**
     * A throwable whose getCause() returns itself, simulating a circular reference.
     */
    private static class SelfReferencingThrowable extends Throwable {
        SelfReferencingThrowable(String message) {
            super(message);
        }

        @Override
        public synchronized Throwable getCause() {
            return this;
        }
    }
}
