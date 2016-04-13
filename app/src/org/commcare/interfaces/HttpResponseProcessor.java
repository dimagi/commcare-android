package org.commcare.interfaces;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface HttpResponseProcessor {
    void processSuccess(int responseCode, InputStream responseData);

    void processRedirection(int responseCode);

    void processClientError(int responseCode);

    void processServerError(int responseCode);

    void processOther(int responseCode);

    void handleIOException(IOException exception);
}
