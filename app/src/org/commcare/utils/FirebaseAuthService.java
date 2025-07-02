package org.commcare.utils;

import android.app.Activity;

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

import androidx.annotation.NonNull;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.PersonalIdApiErrorHandler;
import org.commcare.connect.network.PersonalIdApiHandler;
import org.javarosa.core.services.Logger;

public class FirebaseAuthService implements OtpAuthService {

    private final FirebaseAuth firebaseAuth;
    private final OtpVerificationCallback callback;
    private final Activity activity;
    private final PersonalIdSessionData personalIdSessionData;
    private PhoneAuthOptions.Builder optionsBuilder;
    private String verificationId;

    public FirebaseAuthService(@NonNull Activity activity, @NonNull PersonalIdSessionData sessionData,
            @NonNull OtpVerificationCallback callback) {
        this.callback = callback;
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.activity = activity;
        this.personalIdSessionData = sessionData;

        PhoneAuthProvider.OnVerificationStateChangedCallbacks verificationCallbacks =
                new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        firebaseAuthenticator(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        handleFirebaseException(e);
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
        firebaseAuthenticator(credential);
    }

    @Override
    public void submitOtp(@NonNull String code) {
        new PersonalIdApiHandler() {

            @Override
            protected void onSuccess(PersonalIdSessionData sessionData) {
                callback.onSuccess();
            }

            @Override
            protected void onFailure(PersonalIdApiErrorCodes failureCode, Throwable t) {
                handlePersonalIdApiError(failureCode, t);
            }

        }.validateFirebaseIdToken(activity, code, personalIdSessionData);
    }

    private void handlePersonalIdApiError(PersonalIdApiHandler.PersonalIdApiErrorCodes failureCode, Throwable t) {
        String error = PersonalIdApiErrorHandler.handle(activity, failureCode, t);
        callback.onFailure(OtpErrorType.GENERIC_ERROR, error);
    }

    private void firebaseAuthenticator(PhoneAuthCredential credential) {
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        user.getIdToken(false).addOnCompleteListener(GetTokenResultTask -> {
                            if (GetTokenResultTask.isSuccessful()) {
                                String idToken = GetTokenResultTask.getResult().getToken();
                                callback.onCodeVerified(idToken);
                                submitOtp(idToken);
                            } else {
                                handleFirebaseException(GetTokenResultTask.getException());
                            }
                        });
                    } else {
                        handleFirebaseException(task.getException());
                    }
                });
    }

    private void handleFirebaseException(Exception e) {
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
        if (e != null) {
            Logger.exception("Firebase OTP verification error", e);
        }
    }
}
