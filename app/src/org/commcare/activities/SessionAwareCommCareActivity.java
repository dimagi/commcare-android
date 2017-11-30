package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.CommCareApplication;
import org.commcare.utils.SessionActivityRegistration;
import org.commcare.utils.SessionUnavailableException;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class SessionAwareCommCareActivity<R> extends CommCareActivity<R> {

    private boolean redirectedInOnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            CommCareApplication.instance().getSession();
            onCreateSessionSafe(savedInstanceState);
        } catch (SessionUnavailableException e) {
            redirectedInOnCreate = true;
            SessionActivityRegistration.redirectToLogin(this);
        }
    }

    protected void onCreateSessionSafe(Bundle savedInstanceState) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean redirectedToLogin =
                SessionActivityRegistration.handleOrListenForSessionExpiration(this) ||
                        redirectedInOnCreate;
        if (!redirectedToLogin) {
            onResumeSessionSafe();
        }
    }

    protected void onResumeSessionSafe() {

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        boolean redirectedToLogin =
                SessionActivityRegistration.handleOrListenForSessionExpiration(this);
        if (!redirectedToLogin) {
            onActivityResultSessionSafe(requestCode, resultCode, intent);
        }
    }

    protected void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {

    }

    @Override
    protected void onPause() {
        super.onPause();
        SessionActivityRegistration.unregisterSessionExpirationReceiver(this);
    }
}
