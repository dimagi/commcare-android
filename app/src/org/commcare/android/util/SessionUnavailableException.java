/**
 * 
 */
package org.commcare.android.util;

/**
 * @author ctsims
 *
 */
public class SessionUnavailableException extends RuntimeException {

    public SessionUnavailableException() {
        super();
    }

    public SessionUnavailableException(String message) {
        super(message);
    }
}
