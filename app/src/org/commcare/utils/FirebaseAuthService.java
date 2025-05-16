package org.commcare.utils;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;

import java.util.concurrent.TimeUnit;

public class FirebaseAuthService implements AuthService {

    private final Activity activity;
    private final OTPVerificationCallback callback;
    private String verificationId;

    // Constructor for sending OTP
    public FirebaseAuthService(Activity activity, OTPVerificationCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    // Overloaded constructor for verifying OTP (with verification ID)
    public FirebaseAuthService(Activity activity, OTPVerificationCallback callback, String verificationId) {
        this(activity, callback);
        this.verificationId = verificationId;
    }

    // 🔹 Called to initiate OTP sending
    @Override
    public void sendOTP(String phoneNumber) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        // Auto-verification or Instant verification
                        signInWithCredential(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        callback.onFailure("Verification failed: " + e.getMessage());
                    }

                    @Override
                    public void onCodeSent(@NonNull String id, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = id; // Save verification ID for later use
                        callback.onCodeSent(id);
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    // 🔹 Called to verify the OTP code entered by user
    @Override
    public void verifyOTP(String code) {
        if (verificationId == null || verificationId.isEmpty()) {
            callback.onFailure("Missing verification ID");
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithCredential(credential);
    }

    // 🔐 Signs in using the provided credential and reports via callback
    private void signInWithCredential(PhoneAuthCredential credential) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(activity, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        callback.onSuccess(user); // ✅ Verified
                    } else {
                        String error = (task.getException() != null)
                                ? task.getException().getMessage()
                                : "Unknown error";
                        callback.onFailure("Sign-in failed: " + error);
                    }
                });
    }
}

