package org.commcare.activities;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import org.commcare.CommCareApplication;
import org.commcare.utils.SessionActivityRegistration;
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
            SessionActivityRegistration.redirectToLogin(a);
            return true;
        }
    }

    protected static void onResumeHelper(AppCompatActivity a, SessionAwareInterface sessionAware,
                                         boolean redirectedInOnCreate) {
        boolean redirectedToLogin =
                SessionActivityRegistration.handleOrListenForSessionExpiration(a) ||
                        redirectedInOnCreate;
        if (!redirectedToLogin) {
            sessionAware.onResumeSessionSafe();
        }
    }

    protected static void onActivityResultHelper(AppCompatActivity a, SessionAwareInterface sessionAware,
                                                 int requestCode, int resultCode, Intent intent) {
        boolean redirectedToLogin =
                SessionActivityRegistration.handleOrListenForSessionExpiration(a);
        if (!redirectedToLogin) {
            sessionAware.onActivityResultSessionSafe(requestCode, resultCode, intent);
        }
    }
}
