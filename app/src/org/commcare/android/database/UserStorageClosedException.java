package org.commcare.android.database;

/**
 * Runtime exception signalling that the user storage db has been closed by an
 * expiring session.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UserStorageClosedException extends RuntimeException {
    public UserStorageClosedException() {
        super();
    }

    public UserStorageClosedException(String message) {
        super(message);
    }

    public UserStorageClosedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserStorageClosedException(Throwable cause) {
        super(cause);
    }
}
