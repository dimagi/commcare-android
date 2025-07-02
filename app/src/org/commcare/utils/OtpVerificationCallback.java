package org.commcare.utils;

import com.google.firebase.auth.FirebaseUser;

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
     * Called when OTP verification completes successfully.
     *
     * @param user The authenticated {@link FirebaseUser}
     */
    void onSuccess(FirebaseUser user);

    /**
     * Called when an error occurs during the OTP process.
     *
     * @param message A description of the error that occurred
     */
    void onFailure(OtpErrorType errorType, @Nullable String message);

}
