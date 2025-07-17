package org.commcare.utils;

import androidx.annotation.Nullable;

/**
 * Callback interface for OTP (One-Time Password) verification operations.
 * <p>
 * Implementations of this interface receive notifications about different stages
 * of the OTP process, such as code being sent, successful verification, or failure.
 */
public interface OtpVerificationCallback {

    /**
     * Called when an OTP code has been successfully sent to the user's phone.
     *
     * @param verificationId A unique identifier for this verification session
     */
    void onCodeSent(String verificationId);

    /**
     * Only called when there is an additional verification step locally after receiving the code
     * Specific to FirebaseAuth at the moment.
     *
     */
    void onCodeVerified(String code);


    /**
     * Called when the OTP code is successfully verified with our servers and user is authenticated.
     * This is called when the OTP verification process completes successfully.
     */
    void onSuccess();

    /**
     * Called when an error occurs during the OTP process.
     *
     * @param message A description of the error that occurred
     */
    void onFailure(OtpErrorType errorType, @Nullable String message);
}
