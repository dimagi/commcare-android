package org.commcare.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.util.Log;

import org.commcare.activities.DispatchActivity;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionRegistrationHelper {
    private static final String TAG = SessionRegistrationHelper.class.getSimpleName();

    public static final String USER_SESSION_EXPIRED =
            "org.commcare.dalvik.application.user_session_expired";

    private static final IntentFilter expirationFilter =
            new IntentFilter(SessionRegistrationHelper.USER_SESSION_EXPIRED);

    private static boolean unredirectedSessionExpiration;
    private static final Object registrationLock = new Object();

    private static final BroadcastReceiver userSessionExpiredReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    unredirectedSessionExpiration = false;
                    redirectToLogin(context);
                }
            };

    /**
     * Redirect to login if session expired, otherwise register activity to
     * listen for session expiration broadcasts. Call this method in onResume
     * methods of activities that are session sensitive.
     */
    public static boolean handleSessionExpiration(AppCompatActivity activity) {
        synchronized (registrationLock) {
            if (unredirectedSessionExpiration) {
                unredirectedSessionExpiration = false;
                redirectToLogin(activity);
                return true;
            }
            return false;
        }
    }

    public static void registerSessionExpirationReceiver(AppCompatActivity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(userSessionExpiredReceiver, expirationFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(userSessionExpiredReceiver, expirationFilter);
        }
    }

    /**
     * Stop activity from listening for session expiration broadcasts.  Call
     * this method in onPause methods of activities that are session sensitive.
     */
    public static void unregisterSessionExpirationReceiver(AppCompatActivity activity) {
        try {
            activity.unregisterReceiver(userSessionExpiredReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Trying to unregister the session expiration receiver " +
                    "that wasn't previously registerd.");
        }
    }

    /**
     * Register session expiration in case the app is in the background and
     * doesn't receive the broadcast. To be acted upon in onResume.
     */
    public static void registerSessionExpiration() {
        synchronized (registrationLock) {
            unredirectedSessionExpiration = true;
        }
    }

    public static void unregisterSessionExpiration() {
        synchronized (registrationLock) {
            unredirectedSessionExpiration = false;
        }
    }

    /**
     * Launch the DispatchActivity, clearing the activity backstack down
     * to its first occurrence, which should be at the very bottom of the
     * stack. The backstack clearing is necessary for exiting out of the app
     * if the login activity is cancelled
     */
    public static void redirectToLogin(Context context) {
        Intent i = new Intent(context.getApplicationContext(), DispatchActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(i);
    }
}
