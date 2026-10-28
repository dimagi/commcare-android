package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import org.commcare.CommCareApplication;
import org.commcare.utils.SessionRegistrationHelper;
import org.commcare.utils.SessionUnavailableException;

/**
 * Created by amstone326 on 11/30/17.
 */

public class SessionAwareHelper {

    protected static boolean onCreateHelper(AppCompatActivity a, SessionAwareInterface sessionAware,
                                            Bundle savedInstanceState) {
        try {
            CommCareApplication.instance().getSession();
            sessionAware.onCreateSessionSafe(savedInstanceState);
            return false;
        } catch (SessionUnavailableException e) {
            SessionRegistrationHelper.redirectToLogin(a);
            a.finish();
            return true;
        }
    }

    protected static void onResumeHelper(SessionAwareInterface sessionAware,
                                         SessionExpirationHandler expirationHandler,
                                         boolean redirectedInOnCreate) {
        boolean redirectedToLogin =
                expirationHandler.handlePendingExpiration() ||
                        redirectedInOnCreate;
        if (!redirectedToLogin) {
            try {
                sessionAware.onResumeSessionSafe();
            } catch (SessionUnavailableException e) {
                expirationHandler.handleSessionUnavailable();
            }
        }
    }

    protected static void onActivityResultHelper(AppCompatActivity a, SessionAwareInterface sessionAware,
                                                 SessionExpirationHandler expirationHandler,
                                                 int requestCode, int resultCode, Intent intent) {
        boolean redirectedToLogin = expirationHandler.handlePendingExpiration();
        if (redirectedToLogin) {
            return;
        }
        if (a.getActivityResultRegistry().dispatchResult(requestCode, resultCode, intent)) {
            return;
        }
        sessionAware.onActivityResultSessionSafe(requestCode, resultCode, intent);
    }
}
