package org.commcare.utils;

import android.app.Activity;
import android.content.Context;

import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import org.commcare.dalvik.R;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

public class FirebaseAuthService implements OtpAuthService {

    private final FirebaseAuth firebaseAuth;
    private OtpVerificationCallback callback;
    private PhoneAuthOptions.Builder optionsBuilder;
    private String verificationId;

    public FirebaseAuthService(Activity activity, OtpVerificationCallback callback) {
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
                                        callback.onFailure("Verification failed");
                                    }
                                });
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        callback.onFailure("Verification failed: " + e.getMessage());
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        FirebaseAuthService.this.verificationId = verificationId;
                        callback.onCodeSent(verificationId);
                    }
                };

        this.optionsBuilder = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setTimeout(0L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(verificationCallbacks);
    }

    @Override
    public void requestOtp(String phoneNumber) {
        optionsBuilder.setPhoneNumber(phoneNumber);
        PhoneAuthOptions options = optionsBuilder.build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    @Override
    public void verifyOtp(@NonNull String code, Context context) {
        if (verificationId == null) {
            callback.onFailure(context.getString(R.string.no_verification_id));
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
                            callback.onFailure(context.getString(R.string.incorrect_otp));
                        } else if (e instanceof FirebaseTooManyRequestsException) {
                            callback.onFailure(context.getString(R.string.too_many_attempts));
                        } else if (e instanceof FirebaseAuthMissingActivityForRecaptchaException) {
                            callback.onFailure(context.getString(R.string.missing_activity));
                        } else {
                            // Other unknown failure
                            callback.onFailure(context.getString(R.string.otp_verification_failed) +
                                    (e != null ? e.getMessage() : "Unknown error"));
                        }
                    }
                });
    }

    public void clearCallback() {
        this.callback = null;
    }

}
