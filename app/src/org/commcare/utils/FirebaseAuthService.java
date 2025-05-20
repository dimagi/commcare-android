package org.commcare.utils;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

/**
 * FirebaseAuthService handles phone number authentication using Firebase.
 * It supports sending OTP (One Time Password) and verifying it to authenticate users.
 */
public class FirebaseAuthService implements OtpAuthService {

    private final Activity activity; // Activity context required for Firebase callbacks
    private final OtpVerificationCallback callback; // Callback interface for OTP result events
    private String verificationId; // Stores the verification ID received after sending OTP

    /**
     * Constructor for initiating OTP send process.
     *
     * @param activity Android activity context
     * @param callback Callback to handle OTP events
     */
    public FirebaseAuthService(Activity activity, OtpVerificationCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    /**
     * Constructor for verifying OTP with a known verification ID.
     *
     * @param activity Android activity context
     * @param callback Callback to handle OTP events
     * @param verificationId ID received when OTP was sent
     */
    public FirebaseAuthService(Activity activity, OtpVerificationCallback callback, String verificationId) {
        this(activity, callback);
        this.verificationId = verificationId;
    }

    /**
     * Sends an OTP to the provided phone number using Firebase's phone auth.
     *
     * @param phoneNumber The phone number to which the OTP will be sent
     */
    @Override
    public void requestOtp(String phoneNumber) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(phoneNumber) // Phone number to verify
                .setActivity(activity) // Activity for binding callback
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                    // Called when verification is completed automatically (instant/auto-retrieval)
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        signInWithCredential(credential);
                    }

                    // Called when verification process fails
                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        callback.onFailure("Verification failed: " + e.getMessage());
                    }

                    // Called when OTP has been sent successfully
                    @Override
                    public void onCodeSent(@NonNull String id, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        verificationId = id; // Store verification ID to use during verification
                        callback.onCodeSent(id); // Notify client
                    }
                })
                .build();

        // Start phone number verification process
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    /**
     * Verifies the OTP entered by the user.
     *
     * @param code The OTP code received by the user
     */
    @Override
    public void verifyOtp(String code) {
        if (verificationId == null || verificationId.isEmpty()) {
            callback.onFailure("Missing verification ID");
            return;
        }

        // Create credential using verification ID and OTP code
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithCredential(credential); // Proceed to sign in
    }

    /**
     * Signs in the user using the provided phone auth credential and
     * invokes appropriate callback methods based on result.
     *
     * @param credential Firebase phone auth credential
     */
    private void signInWithCredential(PhoneAuthCredential credential) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(activity, task -> {
                    if (task.isSuccessful()) {
                        // Authentication successful
                        FirebaseUser user = task.getResult().getUser();
                        callback.onSuccess(user);
                    } else {
                        // Authentication failed
                        String error = (task.getException() != null)
                                ? task.getException().getMessage()
                                : "Unknown error";
                        callback.onFailure("Sign-in failed: " + error);
                    }
                });
    }
}
