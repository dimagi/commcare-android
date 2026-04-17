package org.commcare.connect.network;

import androidx.annotation.Nullable;

import java.io.InputStream;

/**
 * Interface for callbacks when network request completes
 */
public interface IApiCallback {
    void processSuccess(int responseCode, InputStream responseData);
    void processFailure(int responseCode, String url, String errorBody, Throwable t);
    void processNetworkFailure(Throwable t);
    void processOldApiError();
    void processTokenUnavailableError();
    void processTokenRequestDeniedError();
}
