package org.commcare.utils;

import com.google.firebase.auth.FirebaseUser;

/** * Callback interface for OTP verification operations. * Implementations receive notifications about the OTP process status. */
public interface OtpVerificationCallback {
    /** Called when an OTP code has been successfully sent to the user's phone.*
     * @param verificationId A unique identifier for this verification session */
    void onCodeSent(String verificationId);

    /** Called when OTP verification completes successfully.*
     * @param user The authenticated user information */
    void onSuccess(FirebaseUser user);

    /** Called when an error occurs during the OTP process.*
     * @param errorMessage A description of the error that occurred */
    void onFailure(String errorMessage);
}
