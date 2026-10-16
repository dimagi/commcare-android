package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.navdrawer.BaseDrawerActivity;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class SessionAwareCommCareActivity<R> extends BaseDrawerActivity<R> implements SessionAwareInterface {

    private final SessionExpirationHandler loginRedirect = new LoginRedirectingExpirationHandler(this);

    private boolean redirectedInOnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.redirectedInOnCreate = SessionAwareHelper.onCreateHelper(this, this, savedInstanceState);
    }

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        getSessionExpirationHandler().startListening();
        SessionAwareHelper.onResumeHelper(this, getSessionExpirationHandler(), redirectedInOnCreate);
    }

    @Override
    public void onResumeSessionSafe() {
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        SessionAwareHelper.onActivityResultHelper(this, this, getSessionExpirationHandler(),
                requestCode, resultCode, intent);
    }

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSessionExpirationHandler().stopListening();
    }

    /**
     * How this activity responds to losing its session after onCreate. Defaults to redirecting to login.
     */
    protected SessionExpirationHandler getSessionExpirationHandler() {
        return loginRedirect;
    }
}
