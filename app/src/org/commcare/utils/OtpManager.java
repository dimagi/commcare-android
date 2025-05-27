package org.commcare.utils;

import android.app.Activity;

import java.util.Objects;

/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 * <p>
 * This class provides a simplified interface for requesting and submitting OTPs
 * by internally managing an appropriate {@link OtpAuthService} implementation.
 */
public class OtpManager {

    // Reference to the authentication service that performs actual OTP operations
    private final OtpAuthService authService;

    /**
     * Constructs an OtpManager by internally selecting an appropriate AuthService implementation.
     *
     * @param activity Context needed for the auth service
     * @param callback Callback interface to notify UI of OTP events
     * @throws NullPointerException if any parameter is null
     */
    public OtpManager(Activity activity, OtpVerificationCallback callback) {
        Objects.requireNonNull(activity, "Activity cannot be null");
        Objects.requireNonNull(callback, "OtpVerificationCallback cannot be null");
        this.authService = new FirebaseAuthService(activity, callback);
    }

    /**
     * Initiates a request to send an OTP to the specified phone number.
     *
     * @param phoneNumber The target phone number
     * @throws IllegalArgumentException if phoneNumber is null, empty, or only whitespace
     */
    public void requestOtp(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty");
        }
        authService.requestOtp(phoneNumber);
    }

    /**
     * Submits an OTP code for verification with the previously received verification ID.
     *
     * @param code The OTP code provided by the user
     * @throws IllegalArgumentException if code is null, empty, or only whitespace
     */
    public void submitOtp(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("OTP code cannot be null or empty");
        }
        authService.verifyOtp(code);
    }
}
