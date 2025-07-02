package org.commcare.utils;

import android.app.Activity;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 */
public class OtpManager {

    private static final String SMS_METHOD_PERSONAL_ID = "personal_id";

    private final OtpAuthService authService;

    public OtpManager(Activity activity, PersonalIdSessionData personalIdSessionData,
            OtpVerificationCallback otpCallback) {
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Initializing OtpManager with SMS method: "
                + personalIdSessionData.getSmsMethod());
        if (SMS_METHOD_PERSONAL_ID.equalsIgnoreCase(personalIdSessionData.getSmsMethod())) {
            authService = new PersonalIdAuthService(activity, personalIdSessionData, otpCallback);
        } else {
            authService = new FirebaseAuthService(activity, personalIdSessionData, otpCallback);
        }
    }

    public void requestOtp(String phoneNumber) {
        authService.requestOtp(phoneNumber);
    }

    public void verifyOtp(String code) {
        if (authService instanceof FirebaseAuthService) {
            authService.verifyOtp(code);
        } else {
            authService.submitOtp(code);
        }
    }

    public void submitOtp(String code) {
        authService.submitOtp(code);
    }
}
