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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.commcare.android.database.connect.models.PersonalIdSessionData;
import org.commcare.connect.network.personalId.PersonalIdApiHandler;
import org.commcare.core.network.AuthInfo;
import org.javarosa.core.services.Logger;

public class FirebaseAuthService implements OtpAuthService {

    private static final String ERROR_SESSION_EXPIRED = "ERROR_SESSION_EXPIRED";
    @VisibleForTesting
    static final String ERROR_NO_VERIFICATION_ID = "NO_VERIFICATION_ID";
    @VisibleForTesting
    static final int MAX_ERROR_CODE_LENGTH = 100;
    private static final Pattern BRACKETED_CODE = Pattern.compile("\\[\\s*([^\\[\\]]*?)\\s*]");

    private final FirebaseAuth firebaseAuth;
    private final OtpVerificationCallback callback;
    private final Activity activity;
    private final AuthInfo authInfo;
    private PhoneAuthOptions.Builder optionsBuilder;
    private String verificationId;

    public FirebaseAuthService(@NonNull Activity activity, @NonNull AuthInfo authInfo,
            @NonNull OtpVerificationCallback callback) {
        this.callback = callback;
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.activity = activity;
        this.authInfo = authInfo;

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
            callback.onFailure(OtpErrorType.GENERIC_ERROR, "Please request OTP again.",
                    ERROR_NO_VERIFICATION_ID);
            return;
        }

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        firebaseAuthenticator(credential);
    }

    @Override
    public void submitOtp(@NonNull String code) {
        new PersonalIdApiHandler<PersonalIdSessionData>() {

            @Override
            public void onSuccess(PersonalIdSessionData sessionData) {
                callback.onSuccess();
            }

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
                callback.onPersonalIdApiFailure(failureCode, t);
            }

        }.validateFirebaseIdToken(activity, code, authInfo);
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

    /**
     * Classifies a Firebase failure. The subclass checks must stay ahead of the
     * {@link FirebaseAuthException} check, since that is what keeps a wrong verification code in
     * INVALID_CREDENTIAL instead of the non-recoverable VERIFICATION_FAILED. Anything unrecognised
     * falls through to GENERIC_ERROR, which is also where Firebase puts backend codes it has no
     * mapping for.
     */
    @VisibleForTesting
    static OtpErrorType errorTypeFrom(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String errorCode = ((FirebaseAuthInvalidCredentialsException) e).getErrorCode();
            return ERROR_SESSION_EXPIRED.equals(errorCode)
                    ? OtpErrorType.SESSION_EXPIRED
                    : OtpErrorType.INVALID_CREDENTIAL;
        } else if (e instanceof FirebaseTooManyRequestsException) {
            return OtpErrorType.TOO_MANY_REQUESTS;
        } else if (e instanceof FirebaseAuthMissingActivityForRecaptchaException) {
            return OtpErrorType.MISSING_ACTIVITY;
        } else if (e instanceof FirebaseAuthException) {
            return OtpErrorType.VERIFICATION_FAILED;
        }
        return OtpErrorType.GENERIC_ERROR;
    }

    /**
     * The Firebase error code behind a failure, for analytics, cut to GA4's 100-character limit:
     * a {@link FirebaseAuthException}'s own code; for an unclassified failure, the backend code
     * Firebase puts in brackets at the end of its message; otherwise the exception's class name.
     */
    @VisibleForTesting
    static String errorCodeFrom(Exception e) {
        if (e == null) {
            return null;
        }
        String code;
        if (e instanceof FirebaseAuthException) {
            code = ((FirebaseAuthException) e).getErrorCode();
        } else {
            code = errorTypeFrom(e) == OtpErrorType.GENERIC_ERROR ? bracketedCodeFrom(e.getMessage()) : null;
            if (code == null) {
                code = e.getClass().getSimpleName();
            }
            if (code.isEmpty()) {
                code = e.getClass().getName();
            }
        }
        return code.length() > MAX_ERROR_CODE_LENGTH ? code.substring(0, MAX_ERROR_CODE_LENGTH) : code;
    }

    private static String bracketedCodeFrom(String message) {
        if (message == null) {
            return null;
        }
        String code = null;
        Matcher matcher = BRACKETED_CODE.matcher(message);
        while (matcher.find()) {
            code = matcher.group(1);
        }
        return code == null || code.isEmpty() ? null : code;
    }

    private void handleFirebaseException(Exception e) {
        OtpErrorType errorType = errorTypeFrom(e);
        String message = errorType == OtpErrorType.GENERIC_ERROR
                ? (e != null ? e.getMessage() : "OTP verification failed")
                : null;
        callback.onFailure(errorType, message, errorCodeFrom(e));
        if (e != null) {
            Logger.exception("Firebase OTP verification error", e);
        }
    }
}
