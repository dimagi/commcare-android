package org.commcare.android.framework;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import org.commcare.dalvik.activities.AppManagerActivity;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class SessionAwareFragmentActivity extends FragmentActivity {

    private boolean fromManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.fromManager = this.getIntent().
                getBooleanExtra(AppManagerActivity.KEY_LAUNCH_FROM_MANAGER, false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!fromManager) {
            SessionActivityRegistration.handleOrListenForSessionExpiration(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!fromManager) {
            SessionActivityRegistration.unregisterSessionExpirationReceiver(this);
        }
    }
}
