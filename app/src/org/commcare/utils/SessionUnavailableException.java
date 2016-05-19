package org.commcare.utils;

/**
 * Signals the expiration of the session created on login that controls
 * liveness of user database and key pool.
 *
 * @author ctsims
 */
public class SessionUnavailableException extends RuntimeException {
    public SessionUnavailableException() {
        super();
    }

    public SessionUnavailableException(String message) {
        super(message);
    }
}
