package org.commcare.android.framework;

import android.os.Bundle;

import org.commcare.android.util.SessionUnavailableException;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class SessionAwareCommCareActivity<R> extends CommCareActivity<R> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            onCreateAware(savedInstanceState);
        } catch (SessionUnavailableException e) {
            SessionActivityRegistration.redirectToLogin(this);
        }
    }

    protected void onCreateAware(Bundle savedInstanceState) throws SessionUnavailableException {

    }

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
