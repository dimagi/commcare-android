package org.commcare.android.framework;

import android.preference.PreferenceActivity;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class SessionAwarePreferenceActivity extends PreferenceActivity {

    @Override
    protected void onResume() {
        super.onResume();

        SessionActivityRegistration.handleOrListenForSessionExpiration(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SessionActivityRegistration.unregisterSessionExpirationReceiver(this);
    }
}
