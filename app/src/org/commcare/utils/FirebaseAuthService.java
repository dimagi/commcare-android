package org.commcare.utils;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class FirebaseAuthService implements OtpAuthService {

    private final FirebaseAuth firebaseAuth;
    private final OtpVerificationCallback callback;
    private PhoneAuthOptions.Builder optionsBuilder;
    private String verificationId;

    public FirebaseAuthService(Activity activity, OtpVerificationCallback callback) {
        this.callback = callback;
        this.firebaseAuth = FirebaseAuth.getInstance();

         this.firebaseAuth.getFirebaseAuthSettings().setAppVerificationDisabledForTesting(true);

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
                .setTimeout(60L, TimeUnit.SECONDS)
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
    public void verifyOtp(String code) {
        if (verificationId == null) {
            callback.onFailure("No verification ID available. Request OTP first.");
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        callback.onSuccess(user);
                    } else {
                        callback.onFailure("OTP verification failed.");
                    }
                });
    }
}
