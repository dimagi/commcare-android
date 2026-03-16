package org.commcare.utils;

import android.app.Activity;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

/**
 * Manager class that wraps authentication service operations for OTP (One-Time Password) functionality.
 */
public class OtpManager {

    public static final String SMS_METHOD_PERSONAL_ID = "personal_id";
    public static final String SMS_METHOD_FIREBASE = "firebase";

    private final OtpAuthService authService;

    public OtpManager(Activity activity, PersonalIdSessionData personalIdSessionData,
            OtpVerificationCallback otpCallback) {
        this(activity,personalIdSessionData, otpCallback,  personalIdSessionData.getSmsMethod());
    }

    public OtpManager(Activity activity, PersonalIdSessionData personalIdSessionData,
            OtpVerificationCallback otpCallback, String otpMethod) {
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Initializing OtpManager with SMS method: "
                + otpMethod);
        if (SMS_METHOD_PERSONAL_ID.equalsIgnoreCase(otpMethod)) {
            authService = new PersonalIdAuthService(activity, personalIdSessionData, otpCallback);
        } else {
            if (!FirebaseUtils.isFirebaseEnabled()) {
                Logger.log(LogTypes.TYPE_WARNING_NETWORK,
                        "Firebase Auth not available - Firebase is not configured");
                otpCallback.onVerificationFailed("Firebase is not configured. OTP via Firebase is unavailable.");
                authService = null;
                return;
            }
            authService = new FirebaseAuthService(activity, personalIdSessionData, otpCallback);
        }
    }

    public void requestOtp(String phoneNumber) {
        if (authService != null) {
            authService.requestOtp(phoneNumber);
        }
    }

    public void verifyOtp(String code) {
        if (authService != null) {
            authService.verifyOtp(code);
        }
    }

}
