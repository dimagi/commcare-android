package org.commcare.activities;

import org.commcare.utils.SessionRegistrationHelper;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class SessionAwarePreferenceActivity extends CommCarePreferenceActivity {

    @Override
    protected void onResume() {
        super.onResume();
        SessionRegistrationHelper.registerSessionExpirationReceiver(this);
        SessionRegistrationHelper.handleSessionExpiration(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SessionRegistrationHelper.unregisterSessionExpirationReceiver(this);
    }
}
