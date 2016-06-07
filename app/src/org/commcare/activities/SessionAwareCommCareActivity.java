package org.commcare.activities;

import android.os.Bundle;
import android.view.Menu;

import org.commcare.utils.SessionActivityRegistration;
import org.commcare.utils.SessionUnavailableException;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class SessionAwareCommCareActivity<R> extends CommCareActivity<R> {

    boolean redirectedToLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            onCreateSessionSafe(savedInstanceState);
        } catch (SessionUnavailableException e) {
            redirectedToLogin = true;
            SessionActivityRegistration.redirectToLogin(this);
        }
    }

    protected void onCreateSessionSafe(Bundle savedInstanceState) {

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


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (redirectedToLogin) {
            return false;
        }
        return onPrepareOptionsMenuSessionSafe(menu);
    }

    public boolean onPrepareOptionsMenuSessionSafe(Menu menu) {
        // to be implemented by subclasses
        return true;
    }
}
