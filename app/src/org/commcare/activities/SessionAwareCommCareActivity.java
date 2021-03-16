package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.utils.SessionRegistrationHelper;

/**
 * Manage redirection to login screen when session expiration occurs.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class SessionAwareCommCareActivity<R> extends CommCareActivity<R> implements SessionAwareInterface {

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
        SessionRegistrationHelper.registerSessionExpirationReceiver(this);
        SessionAwareHelper.onResumeHelper(this, this, redirectedInOnCreate);
    }

    @Override
    public void onResumeSessionSafe() {
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        SessionAwareHelper.onActivityResultHelper(this, this, requestCode, resultCode, intent);
    }

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
    }

    @Override
    protected void onPause() {
        super.onPause();
        SessionRegistrationHelper.unregisterSessionExpirationReceiver(this);
    }
}
