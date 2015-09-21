package org.commcare.android.util;

/**
 * Signals that the session controlling the current state of the application
 * hasn't been initialized. This session stores info on the current
 * navigation position of the user.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionStateUninitException extends RuntimeException {
    public SessionStateUninitException() { super(); }
    public SessionStateUninitException(String message) { super(message); }
    public SessionStateUninitException(String message, Throwable cause) { super(message, cause); }
    public SessionStateUninitException(Throwable cause) { super(cause); }
}

