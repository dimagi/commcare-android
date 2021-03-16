package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;

import org.commcare.utils.SessionRegistrationHelper;

/**
 * Reproduction of SessionAwareCommCareActivity, but for an activity that must extend ListActivity
 *
 * @author Aliza Stone
 */
public abstract class SessionAwareListActivity extends CommcareListActivity implements SessionAwareInterface {

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
        SessionRegistrationHelper.registerSessionExpirationReceiver(this);
        SessionAwareHelper.onResumeHelper(this, this, redirectedInOnCreate);
    }

    @Override
    public void onResumeSessionSafe() {
    }

    @Override
    protected void onPause() {
        super.onPause();
        SessionRegistrationHelper.unregisterSessionExpirationReceiver(this);
    }

    @Override
    public void onActivityResultSessionSafe(int requestCode, int resultCode, Intent intent) {
    }

}
