package org.commcare.android.analytics;

import org.commcare.android.util.SessionStateUninitException;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.javarosa.core.model.User;

import java.io.Serializable;
import java.util.Date;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class XPathErrorEntry implements Serializable {
    public final Date time;
    public final String expression;
    public final String username;
    public final String errorMessage;
    public final String sessionFramePath;

    protected XPathErrorEntry(String expression, String errorMessage) {
        this.expression = expression;
        this.errorMessage = errorMessage;

        this.time = new Date();
        this.username = getUsername();

        // TODO PLM
        this.sessionFramePath = getCurrentSession();
    }

    private static String getUsername() {
        User currentUser;
        try {
            currentUser = CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException e) {
            currentUser = null;
        }
        if (currentUser != null) {
            return currentUser.getUsername();
        } else {
            return "no user";
        }
    }

    private static String getCurrentSession() {
        CommCareSession currentSession;
        try {
            currentSession = CommCareApplication._().getCurrentSession();
            // TODO PLM:
            // return currentSession.getFrame().sessionPathString();
            return "";
        } catch (SessionStateUninitException e) {
            return "";
        }
    }

    @Override
    public String toString() {
        return time.toString() + " | " + username + " encountered " +
                errorMessage + " caused by " + expression +
                "\n" + "session: " + sessionFramePath;
    }
}
