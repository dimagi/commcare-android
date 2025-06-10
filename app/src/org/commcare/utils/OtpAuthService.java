package org.commcare.utils;

/**
 * Interface defining the contract for OTP-based authentication operations.
 * <p>
 * Implementations of this interface are responsible for sending OTP codes to users
 * and verifying the OTP codes they enter.
 */
public interface OtpAuthService {

    /**
     * Sends an OTP to the specified phone number.
     *
     * @param phoneNumber The recipient's phone number in a valid format
     */
    void requestOtp(String phoneNumber);

    /**
     * Verifies the OTP code entered by the user.
     *
     * @param code The OTP code to verify
     */
    void verifyOtp(String code);
}
