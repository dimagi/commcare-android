package org.commcare.utils;

import com.google.firebase.auth.PhoneAuthOptions;

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
     * @param optionsBuilder Pre-configured PhoneAuthOptions.Builder used to build OTP authentication options
     * @param callback Callback interface to notify UI of OTP events
     * @throws NullPointerException if any parameter is null
     */
    public OtpManager(PhoneAuthOptions.Builder optionsBuilder, OtpVerificationCallback callback) {
        Objects.requireNonNull(optionsBuilder, "PhoneAuthOptions.Builder cannot be null");
        Objects.requireNonNull(callback, "OtpVerificationCallback cannot be null");

        // Internally select FirebaseAuthService with preconfigured builder
        this.authService = new FirebaseAuthService(optionsBuilder, callback);
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
