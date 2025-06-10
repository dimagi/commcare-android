package org.commcare.utils;

import android.app.Activity;


/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 */
public class OtpManager {

    private final OtpAuthService authService;

    /**
     * Constructs an OtpManager by instantiating a default FirebaseAuthService internally.
     *
     * @param activity The calling activity, required by FirebaseAuth
     * @param callback Callback to handle OTP verification events
     */
    public OtpManager(Activity activity, OtpVerificationCallback callback) {
        this.authService = new FirebaseAuthService(activity, callback);
    }

    public void requestOtp(String phoneNumber) {
        authService.requestOtp(phoneNumber);
    }

    public void submitOtp(String code) {
        authService.verifyOtp(code);
    }
}
