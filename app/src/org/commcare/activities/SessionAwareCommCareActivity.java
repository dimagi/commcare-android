package org.commcare.activities;

import android.os.Bundle;

import org.commcare.utils.SessionActivityRegistration;
import org.commcare.utils.SessionUnavailableException;

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
            onCreateSessionSafe(savedInstanceState);
        } catch (SessionUnavailableException e) {
            SessionActivityRegistration.redirectToLogin(this);
        }
    }

    protected void onCreateSessionSafe(Bundle savedInstanceState) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean redirectedToLogin =
                SessionActivityRegistration.handleOrListenForSessionExpiration(this);
        if (!redirectedToLogin) {
            onResumeSessionSafe();
        }
    }

     protected void onResumeSessionSafe() {

     }

    @Override
    protected void onPause() {
        super.onPause();

        SessionActivityRegistration.unregisterSessionExpirationReceiver(this);
    }
}
