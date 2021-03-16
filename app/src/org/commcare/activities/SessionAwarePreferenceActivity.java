package org.commcare.activities;

import android.preference.PreferenceActivity;

import org.commcare.utils.SessionActivityRegistration;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionAwarePreferenceActivity extends CommCarePreferenceActivity {

    @Override
    protected void onResume() {
        super.onResume();
        SessionActivityRegistration.registerSessionExpirationReceiver(this);
        SessionActivityRegistration.handleOrListenForSessionExpiration(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SessionActivityRegistration.unregisterSessionExpirationReceiver(this);
    }
}
