package org.commcare.activities;

import android.app.ListActivity;
import android.os.Bundle;

import org.commcare.CommCareApplication;
import org.commcare.utils.SessionActivityRegistration;
import org.commcare.utils.SessionUnavailableException;

/**
 * Reproduction of SessionAwareCommCareActivity, but for an activity that must extend ListActivity
 *
 * @author Aliza Stone
 */
public abstract class SessionAwareListActivity extends ListActivity {

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
    protected void onPause() {
        super.onPause();

        SessionActivityRegistration.unregisterSessionExpirationReceiver(this);
    }

}
