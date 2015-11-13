package org.commcare.android.analytics;

import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.User;

import java.io.Serializable;
import java.util.Date;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class XPathErrorEntry implements Serializable {
    private final Date time;
    private final String expression;
    private final String username;
    private final String errorMessage;
    private final String session;

    protected XPathErrorEntry(String expression, String errorMessage) {
        this.expression = expression;
        this.errorMessage = errorMessage;

        this.time = new Date();
        this.username = getUsername();

        // TODO PLM
        this.session = "";
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

    @Override
    public String toString() {
        return time.toString() + " | " + username + " encountered " +
                errorMessage + " caused by " + expression +
                "\n" + "session: " + session;
    }
}
