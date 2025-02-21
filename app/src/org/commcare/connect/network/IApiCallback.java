package org.commcare.connect.network;

import java.io.IOException;
import java.io.InputStream;

/**
 * Interface for callbacks when network request completes
 */
public interface IApiCallback {
    void processSuccess(int responseCode, InputStream responseData);

    void processFailure(int responseCode, IOException e);

    void processNetworkFailure();

    void processOldApiError();
}
