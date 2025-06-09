package org.commcare.utils;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class FirebaseAuthService implements OtpAuthService {

    private final FirebaseAuth firebaseAuth;
    private OtpVerificationCallback callback;
    private PhoneAuthOptions.Builder optionsBuilder;
    private String verificationId;

    public FirebaseAuthService(@NonNull Activity activity, @NonNull OtpVerificationCallback callback) {
        this.callback = callback;
        this.firebaseAuth = FirebaseAuth.getInstance();

        PhoneAuthProvider.OnVerificationStateChangedCallbacks verificationCallbacks =
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        firebaseAuth.signInWithCredential(credential)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        FirebaseUser user = task.getResult().getUser();
                                        callback.onSuccess(user);
                                    } else {
                                        callback.onFailure(OtpErrorType.GENERIC_ERROR, "Verification failed");
                                    }
                                });
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            callback.onFailure(OtpErrorType.INVALID_CREDENTIAL, null);
                        } else if (e instanceof FirebaseTooManyRequestsException) {
                            callback.onFailure(OtpErrorType.TOO_MANY_REQUESTS, null);
                        } else if (e instanceof FirebaseAuthMissingActivityForRecaptchaException) {
                            callback.onFailure(OtpErrorType.MISSING_ACTIVITY, null);
                        } else if (e instanceof FirebaseAuthException) {
                            callback.onFailure(OtpErrorType.VERIFICATION_FAILED, null);
                        } else {
                            callback.onFailure(OtpErrorType.GENERIC_ERROR, e.getMessage());
                        }
                    }

                    @Override
                    public void onCodeSent(@NonNull String verId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = verId;
                        callback.onCodeSent(verId);
                    }
                };

        this.optionsBuilder = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setTimeout(0L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(verificationCallbacks);
    }

    @Override
    public void requestOtp(@NonNull String phoneNumber) {
        optionsBuilder.setPhoneNumber(phoneNumber);
        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build());
    }

    @Override
    public void verifyOtp(@NonNull String code) {
        if (verificationId == null) {
            callback.onFailure(OtpErrorType.GENERIC_ERROR, "Please request OTP again.");
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        callback.onSuccess(user);
                    } else {
                        Exception e = task.getException();
                        if (e instanceof FirebaseAuthInvalidCredentialsException) {
                            callback.onFailure(OtpErrorType.INVALID_CREDENTIAL, null);
                        } else if (e instanceof FirebaseTooManyRequestsException) {
                            callback.onFailure(OtpErrorType.TOO_MANY_REQUESTS, null);
                        } else if (e instanceof FirebaseAuthMissingActivityForRecaptchaException) {
                            callback.onFailure(OtpErrorType.MISSING_ACTIVITY, null);
                        } else if (e instanceof FirebaseAuthException) {
                            callback.onFailure(OtpErrorType.VERIFICATION_FAILED, null);
                        } else {
                            callback.onFailure(OtpErrorType.GENERIC_ERROR,
                                    e != null ? e.getMessage() : "OTP verification failed");
                        }
                    }
                });
    }

    @Override
    public void clearCallback() {
        this.callback = null;
    }
}
