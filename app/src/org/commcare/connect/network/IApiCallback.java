package org.commcare.connect.network;

import java.io.InputStream;

/**
 * Interface for callbacks when network request completes
 */
public interface IApiCallback {
    void processSuccess(int responseCode, InputStream responseData);
    void processFailure(int responseCode);
    void processNetworkFailure();
    void processOldApiError();
    void processTokenUnavailableError();
    void processTokenRequestDeniedError();
}
