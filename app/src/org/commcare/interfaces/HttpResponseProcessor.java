package org.commcare.interfaces;

import java.io.IOException;
import java.io.InputStream;

/**
 * Callbacks for different http response result codes
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public interface HttpResponseProcessor {
    /**
     * Http response was in the 200s
     */
    void processSuccess(int responseCode, InputStream responseData);

    /**
     * Http response was in the 300s
     */
    void processRedirection(int responseCode);

    /**
     * Http response was in the 400s.
     *
     * Can represent authentication issues, data parity issues between client
     * and server, among other things
     */
    void processClientError(int responseCode);

    /**
     * Http response was in the 500s
     */
    void processServerError(int responseCode);

    /**
     * Http response that had a code not in the 200-599 range
     */
    void processOther(int responseCode);

    /**
     * A I/O issue occurred while processing the http request or response
     */
    void handleIOException(IOException exception);
}
