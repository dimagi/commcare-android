package org.commcare.network;

import android.content.SharedPreferences;
import android.support.v4.util.Pair;

import org.commcare.CommCareApplication;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpUtils {
    public static Pair<User, String> getUserAndDomain(boolean isAuthenticatedRequest) {
        User user = null;
        String domain = null;
        if (isAuthenticatedRequest) {
            try {
                user = CommCareApplication._().getSession().getLoggedInUser();
            } catch (SessionUnavailableException sue) {
                throw new RuntimeException("Can't find user to make authenticated http request.");
            }
            SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
            if (prefs.contains(HttpRequestGenerator.USER_DOMAIN_SUFFIX)) {
                domain = prefs.getString(HttpRequestGenerator.USER_DOMAIN_SUFFIX, null);
            }
        }
        return new Pair<>(user, domain);
    }
}
