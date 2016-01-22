package org.commcare.android.framework;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import org.commcare.dalvik.activities.DispatchActivity;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionActivityRegistration {
    private static final String TAG = SessionActivityRegistration.class.getSimpleName();

    public static final String USER_SESSION_EXPIRED =
            "org.commcare.dalvik.application.user_session_expired";

    private static final IntentFilter expirationFilter =
            new IntentFilter(SessionActivityRegistration.USER_SESSION_EXPIRED);

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
    public static void handleOrListenForSessionExpiration(Activity activity) {
        activity.registerReceiver(userSessionExpiredReceiver, expirationFilter);

        synchronized (registrationLock) {
            if (unredirectedSessionExpiration) {
                unredirectedSessionExpiration = false;
                redirectToLogin(activity);
            }
        }
    }

    /**
     * Stop activity from listening for session expiration broadcasts.  Call
     * this method in onPause methods of activities that are session sensitive.
     */
    public static void unregisterSessionExpirationReceiver(Activity activity) {
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

    /**
     * Launch the DispatchActivity, clearing the activity backstack down
     * to its first occurrence, which should be at the very bottom of the
     * stack. The backstack clearing is necessary for exiting out of the app
     * if the login activity is cancelled
     */
    protected static void redirectToLogin(Context context) {
        Intent i = new Intent(context.getApplicationContext(), DispatchActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(i);
    }
}
