package org.commcare.utils;

import android.app.Activity;

/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 */
public class OtpManager {

    private final OtpAuthService authService;
    private OtpVerificationCallback callback;

    /**
     * Constructs an OtpManager by instantiating a default FirebaseAuthService internally.
     *
     * @param activity The calling activity, required by FirebaseAuth
     * @param callback Callback to handle OTP verification events
     */
    public OtpManager(Activity activity, OtpVerificationCallback callback) {
        this.callback = callback;
        this.authService = new FirebaseAuthService(activity, callback);
    }

    public void requestOtp(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }
        authService.requestOtp(phoneNumber);
    }

    public void submitOtp(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP code cannot be null or empty");
        }
        authService.verifyOtp(code);
    }

    public void cancel() {
        if (authService instanceof FirebaseAuthService) {
            ((FirebaseAuthService) authService).clearCallback();
        }
        this.callback = null;
    }
}
