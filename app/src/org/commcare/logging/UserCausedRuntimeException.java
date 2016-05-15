package org.commcare.logging;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UserCausedRuntimeException extends RuntimeException {
    public UserCausedRuntimeException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
