package org.commcare.utils;

import android.app.Activity;


/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 */
public class OtpManager {

    private final OtpAuthService authService;

    /**
     * Constructs an OtpManager by instantiating a default FirebaseAuthService internally.
     */
    public OtpManager(OtpAuthService authService) {
        this.authService = authService;
    }

    public void requestOtp(String phoneNumber) {
        authService.requestOtp(phoneNumber);
    }

    public void verifyOtp(String code) {
        authService.verifyOtp(code);
    }

    public void submitOtp(String code) {
        authService.submitOtp(code);
    }
}
