package org.commcare.utils;

import android.app.Activity;

import androidx.annotation.Nullable;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.core.network.AuthInfo;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 * The caller picks the SMS method; {@code authInfo} authenticates the PersonalID calls (session token
 * during signup, basic auth in Manage Profile).
 */
public class OtpManager {

    public static final String SMS_METHOD_PERSONAL_ID = "personal_id";
    public static final String SMS_METHOD_FIREBASE = "firebase";

    private final OtpAuthService authService;

    public OtpManager(Activity activity, AuthInfo authInfo,
            @Nullable PersonalIdSessionData personalIdSessionData,
            OtpVerificationCallback otpCallback, String otpMethod) {
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Initializing OtpManager with SMS method: "
                + otpMethod);
        if (SMS_METHOD_PERSONAL_ID.equalsIgnoreCase(otpMethod)) {
            authService = new PersonalIdAuthService(activity, authInfo, personalIdSessionData, otpCallback);
        } else {
            authService = new FirebaseAuthService(activity, authInfo, personalIdSessionData, otpCallback);
        }
    }

    public void requestOtp(String phoneNumber) {
        authService.requestOtp(phoneNumber);
    }

    public void verifyOtp(String code) {
        authService.verifyOtp(code);
    }

}
