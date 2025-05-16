package org.commcare.utils;

import com.google.firebase.auth.FirebaseUser;

public interface OTPVerificationCallback {
    void onCodeSent(String verificationId);
    void onSuccess(FirebaseUser user);
    void onFailure(String errorMessage);
}
