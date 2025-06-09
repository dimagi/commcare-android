package org.commcare.utils;

import android.app.Activity;
import android.content.Context;

import org.commcare.dalvik.R;


/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 */
public class OtpManager {

    private final OtpAuthService authService;

    /**
     * Constructs an OtpManager by instantiating a default FirebaseAuthService internally.
     *
     * @param activity The calling activity, required by FirebaseAuth
     * @param callback Callback to handle OTP verification events
     */
    public OtpManager(Activity activity, OtpVerificationCallback callback) {
        this.authService = new FirebaseAuthService(activity, callback);
    }

    public void requestOtp(String phoneNumber, Context context) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException(context.getString(R.string.phone_number_cannot_be_null_or_empty));
        }
        authService.requestOtp(phoneNumber);
    }

    public void submitOtp(String code, Context context) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException(context.getString(R.string.otp_code_cannot_be_null_or_empty));
        }
        authService.verifyOtp(code,context);
    }

    public void cancel() {
        if (authService instanceof FirebaseAuthService) {
            ((FirebaseAuthService) authService).clearCallback();
        }
    }
}
