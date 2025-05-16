package org.commcare.utils;


public class OTPManager {
    private final AuthService authService;

    public OTPManager(AuthService authService) {
        this.authService = authService;
    }

    public void requestOTP(String phoneNumber) {
        authService.sendOTP(phoneNumber);
    }

    public void submitOTP(String code) {
        authService.verifyOTP(code);
    }
}
