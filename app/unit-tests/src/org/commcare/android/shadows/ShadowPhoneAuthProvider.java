package org.commcare.android.shadows;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Replaces the static {@link PhoneAuthProvider#verifyPhoneNumber(PhoneAuthOptions)} so a test can
 * make Firebase report a chosen failure instead of reaching the network. This lets a test drive the
 * real OTP request cycle rather than invoking the fragment's callback itself: requestOtp goes
 * through OtpManager into FirebaseAuthService, which classifies the exception and calls back.
 * <p>
 * The failure is posted to the main looper because Firebase reports verification failures
 * asynchronously. A test advances it with {@code ShadowLooper.idleMainLooper()}, which makes the
 * state before and after the callback separately observable.
 * <p>
 * Written in Java rather than Kotlin because Robolectric discovers {@code @Implementation} methods
 * on the shadow class itself, and Kotlin's {@code @JvmStatic} bridge would not carry the annotation.
 * <p>
 * Reads the callbacks via {@code PhoneAuthOptions.zze()}, the only accessor Firebase exposes. That
 * name is obfuscated, so a firebase-auth upgrade may require updating it.
 */
@Implements(PhoneAuthProvider.class)
public class ShadowPhoneAuthProvider {

    /**
     * Guards against code under test that re-requests from Firebase without bound. Each posted
     * failure can trigger another request, so an unbounded loop would spin idleMainLooper() until
     * the test JVM runs out of memory instead of failing an assertion.
     */
    private static final int MAX_REQUESTS = 10;

    private static FirebaseException failure;
    private static String sentVerificationId;
    private static int requestCount;

    /** Makes every subsequent verifyPhoneNumber call report {@code e} to onVerificationFailed. */
    public static void failWith(FirebaseException e) {
        failure = e;
    }

    /**
     * Makes every subsequent verifyPhoneNumber call report success to onCodeSent, as Firebase does
     * once it has handed the SMS off for delivery.
     */
    public static void sendCodeWith(String verificationId) {
        sentVerificationId = verificationId;
    }

    /** Number of verifyPhoneNumber calls since the last {@link #reset()}. */
    public static int getRequestCount() {
        return requestCount;
    }

    public static void reset() {
        failure = null;
        sentVerificationId = null;
        requestCount = 0;
    }

    @Implementation
    public static void verifyPhoneNumber(PhoneAuthOptions options) {
        requestCount++;
        if (requestCount > MAX_REQUESTS) {
            throw new AssertionError("Firebase was asked to send an OTP " + requestCount
                    + " times; the code under test is re-requesting without bound.");
        }
        if (failure != null) {
            FirebaseException reported = failure;
            post(() -> options.zze().onVerificationFailed(reported));
        } else if (sentVerificationId != null) {
            String verificationId = sentVerificationId;
            // The resend token is only stored by the caller, so it is safe to omit here.
            post(() -> options.zze().onCodeSent(verificationId, null));
        }
    }

    private static void post(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
