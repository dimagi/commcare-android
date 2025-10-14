package org.commcare.utils

interface OtpAuthService {

    /**
     * Sends an OTP to the specified phone number.
     *
     * @param phoneNumber The recipient's phone number in a valid format
     */
    fun requestOtp(phoneNumber: String)

    /**
     * Verifies the OTP code entered by the user if needed
     *
     * @param code The OTP code to verify
     */
    fun verifyOtp(code: String)

    /**
     * Submits the OTP code for verification.
     * This method is typically called after the user has entered the OTP code.
     *
     * @param code                  The OTP code to submit
     */
    fun submitOtp(code: String)
}
