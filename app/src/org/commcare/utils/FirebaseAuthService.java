package org.commcare.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class FirebaseAuthService implements OtpAuthService {

    private final FirebaseAuth firebaseAuth;
    private final PhoneAuthOptions.Builder baseOptionsBuilder;
    private final OtpVerificationCallback callback;
    private String verificationId;

    public FirebaseAuthService(@NonNull PhoneAuthOptions.Builder baseOptionsBuilder,
                               @NonNull OtpVerificationCallback callback) {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.baseOptionsBuilder = Objects.requireNonNull(baseOptionsBuilder, "PhoneAuthOptions.Builder cannot be null");
        this.callback = Objects.requireNonNull(callback, "OtpVerificationCallback cannot be null");
    }

    @Override
    public void requestOtp(String phoneNumber) {
        PhoneAuthOptions options = baseOptionsBuilder
                .setPhoneNumber(phoneNumber)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        Log.d("FirebaseAuthService", "Auto-verification completed.");
                        signInWithCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        Log.e("FirebaseAuthService", "Verification failed: " + e.getMessage(), e);
                        callback.onFailure("Verification failed: " + e.getMessage());
                    }

                    @Override
                    public void onCodeSent(@NonNull String verificationId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        Log.d("FirebaseAuthService", "Code sent: " + verificationId);
                        FirebaseAuthService.this.verificationId = verificationId;
                        callback.onCodeSent(verificationId);
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    @Override
    public void verifyOtp(String code) {
        if (verificationId == null || verificationId.isEmpty()) {
            callback.onFailure("Verification ID is missing.");
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithCredential(credential);
    }

    private void signInWithCredential(PhoneAuthCredential credential) {
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        if (user != null) {
                            callback.onSuccess(user);
                        } else {
                            callback.onFailure("Failed to retrieve user.");
                        }
                    } else {
                        callback.onFailure("Sign-in failed: " + Objects.requireNonNull(task.getException()).getMessage());
                    }
                });
    }
}
