package org.commcare.utils;

import java.util.Objects;

/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 * <p>
 * This class provides a simplified interface for requesting and submitting OTPs
 * by delegating actual authentication logic to an implementation of the {@link OtpAuthService} interface.
 */
public class OtpManager {

    // Reference to the authentication service that performs actual OTP operations
    private final OtpAuthService authService;

    /**
     * Constructs an OtpManager with the specified AuthService implementation.
     *
     * @param authService An implementation of AuthService used to send and verify OTPs
     * @throws NullPointerException if authService is null
     */
    public OtpManager(OtpAuthService authService) {
        this.authService = Objects.requireNonNull(authService, "AuthService cannot be null");
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
