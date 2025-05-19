package org.commcare.utils;

/**
 * AuthService is an interface that defines the contract for OTP-based authentication operations.Implementations of this interface are responsible for sending OTP codes to users and verifying the OTPs they enter
 */
public interface AuthService {

    /**
     * Sends an OTP to the specified phone number.
     *
     * @param phoneNumber The recipient's phone number in a valid format
     */
    void sendOtp(String phoneNumber);

    /**
     * Verifies the OTP code entered by the user.
     *
     * @param code The OTP code to verify
     */
    void verifyOtp(String code);
}
