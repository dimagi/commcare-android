package org.commcare.utils;

import android.app.Activity;

import org.commcare.android.database.connect.models.PersonalIdSessionData;

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
     * Verifies the OTP code entered by the user if needed
     *
     * @param code The OTP code to verify
     */
    void verifyOtp(String code);

    /**
     * Submits the OTP code for verification.
     * This method is typically called after the user has entered the OTP code.
     *
     * @param code                  The OTP code to submit
     */
    void submitOtp(String code);
}
