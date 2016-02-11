package org.commcare.android.logging;

import android.support.v4.util.Pair;

import org.commcare.android.util.SessionStateUninitException;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Profile;

/**
 * Created by amstone326 on 2/11/16.
 */
public class LoggingUtils {

    protected static Pair<Integer, String> lookupCurrentAppVersionAndId() {
        CommCareApp app = CommCareApplication._().getCurrentApp();

        if (app != null) {
            Profile profile = app.getCommCarePlatform().getCurrentProfile();
            return new Pair<>(profile.getVersion(), profile.getUniqueId());
        }

        return new Pair<>(-1, "");
    }

    protected static String getCurrentSession() {
        CommCareSession currentSession;
        try {
            currentSession = CommCareApplication._().getCurrentSession();
            return currentSession.getFrame().toString();
        } catch (SessionStateUninitException e) {
            return "";
        }
    }

}
