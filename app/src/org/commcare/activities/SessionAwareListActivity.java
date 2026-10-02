package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

/**
 * Reproduction of SessionAwareCommCareActivity, but for an activity that must extend ListActivity
 *
 * @author Aliza Stone
 */
public abstract class SessionAwareListActivity extends CommcareListActivity implements SessionAwareInterface {

    private final SessionExpirationHandler loginRedirect = new LoginRedirectingExpirationHandler(this);

    private boolean redirectedInOnCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        redirectedInOnCreate = SessionAwareHelper.onCreateHelper(this, this, savedInstanceState);
    }

    @Override
    public void onCreateSessionSafe(Bundle savedInstanceState) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        loginRedirect.startListening();
        SessionAwareHelper.onResumeHelper(this, loginRedirect, redirectedInOnCreate);
    }

    @Override
    public void onResumeSessionSafe() {
    }

    @Override
    protected void onPause() {
        super.onPause();
        loginRedirect.stopListening();
    }

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
    }

}
