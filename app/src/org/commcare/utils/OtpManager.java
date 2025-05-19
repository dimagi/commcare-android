package org.commcare.utils;

/**+ * Manager class that wraps authentication service operations for OTP functionality.+ * Provides a simplified interface for requesting and submitting OTPs. */
public class OtpManager {

    // Reference to the authentication service that performs actual OTP operations
    private final AuthService authService;

    /**
     * Constructs an OtpManager with the specified AuthService implementation.
     *
     * @param authService An implementation of AuthService used to send and verify OTPs
     * @throws IllegalArgumentException if authService is null
     */
    public OtpManager(AuthService authService) {
        if (authService == null) {
            throw new IllegalArgumentException("AuthService cannot be null");
        }
        this.authService = authService;
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
        authService.sendOtp(phoneNumber);
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
