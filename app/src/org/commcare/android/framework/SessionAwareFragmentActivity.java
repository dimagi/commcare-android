package org.commcare.android.framework;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import org.commcare.dalvik.activities.LoginActivity;

public abstract class SessionAwareFragmentActivity extends FragmentActivity {
    private static BroadcastReceiver userSessionExpiredReceiver = null;
    private static boolean unredirectedSessionExpiration;

    public static final String USER_SESSION_EXPIRED =
        "org.commcare.dalvik.application.user_session_expired";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        buildAndSetBroadcaseReceivers();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (unredirectedSessionExpiration) {
            unredirectedSessionExpiration = false;
            returnToLogin();
        } else {
            registerReceiver(userSessionExpiredReceiver,
                    new IntentFilter(SessionAwareFragmentActivity.USER_SESSION_EXPIRED));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterReceiver(userSessionExpiredReceiver);
    }

    protected void returnToLogin() {
        Intent i = new Intent(getApplicationContext(), LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(i);
    }

    public static void registerSessionExpiration() {
        unredirectedSessionExpiration = true;
    }

    private void buildAndSetBroadcaseReceivers() {
        if (userSessionExpiredReceiver == null) {
            userSessionExpiredReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    returnToLogin();
                }
            };
        }
    }
}
