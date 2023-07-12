package org.commcare.utils;

/**
 * Flags an inconsistency in the current session
 *
 */
public class SessionStateInconsistentException extends RuntimeException {
    public SessionStateInconsistentException() {
        super();
    }

    public SessionStateInconsistentException(String message) {
        super(message);
    }
}
