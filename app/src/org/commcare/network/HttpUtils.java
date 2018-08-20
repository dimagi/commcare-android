package org.commcare.network;

import org.commcare.CommCareApplication;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.utils.CredentialUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;

import javax.annotation.Nullable;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpUtils {

    public static String getCredential(@Nullable Pair<String, String> usernameAndPasswordToAuthWith) {
        String credential;
        if (usernameAndPasswordToAuthWith == null) {
            // use the logged in user
            User user = getUser();
            credential = getCredential(user.getUsername(), user.getCachedPwd());
        } else {
            credential = getCredential(usernameAndPasswordToAuthWith.first, usernameAndPasswordToAuthWith.second);
        }
        return credential;
    }

    private static User getUser() {
        User user;
        try {
            user = CommCareApplication.instance().getSession().getLoggedInUser();
        } catch (SessionUnavailableException sue) {
            throw new RuntimeException("Can't find user to make authenticated http request.");
        }
        return user;
    }

    private static String buildAppPassword(String password) {
        if (DeveloperPreferences.useObfuscatedPassword()) {
            return CredentialUtil.wrap(password);
        }
        return password;
    }

    private static String buildDomainUser(String username) {
        if (username != null && !username.contains("@")) {
            String domain = HiddenPreferences.getUserDomain();
            if (domain != null) {
                username += "@" + domain;
            }
        }
        return username;
    }

    private static String getCredential(String username, String password) {
        if (username == null || password == null) {
            return null;
        } else {
            return okhttp3.Credentials.basic(buildDomainUser(username), buildAppPassword(password));
        }
    }
}
