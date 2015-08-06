package org.commcare.android.framework;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import org.commcare.dalvik.activities.LoginActivity;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionActivityRegistration {
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
                    returnToLogin(context);
                }
            };

    /**
     * Redirect to login if session expired, otherwise register activity to
     * listen for session expiration broadcasts. Call this method in onResume
     * methods of activities that are session sensitive.
     */
    public static void handleOrListenForSessionExpiration(Activity activity) {
        synchronized (registrationLock) {
            if (unredirectedSessionExpiration) {
                unredirectedSessionExpiration = false;
                returnToLogin(activity);
            }
        }
        activity.registerReceiver(userSessionExpiredReceiver, expirationFilter);
    }

    /**
     * Stop activity from listening for session expiration broadcasts.  Call
     * this method in onPause methods of activities that are session sensitive.
     */
    public static void unregisterSessionExpirationReceiver(Activity activity) {
        activity.unregisterReceiver(userSessionExpiredReceiver);
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

    public static void returnToLogin(Context context) {
        Intent i = new Intent(context.getApplicationContext(), LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        i.putExtra(LoginActivity.REDIRECT_TO_HOMESCREEN, true);
        context.startActivity(i);
    }
}
