package org.commcare.network;

import org.commcare.CommCareApplication;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.HiddenCommCarePreferences;
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
            // User already logged in
            Pair<User, String> userAndDomain = getUserAndDomain(true);
            credential = getCredential(userAndDomain.first, userAndDomain.second);
        } else {
            credential = getCredential(
                    buildDomainUser(usernameAndPasswordToAuthWith.first),
                    buildAppPassword(usernameAndPasswordToAuthWith.second));
        }
        return credential;
    }

    private static Pair<User, String> getUserAndDomain(boolean isAuthenticatedRequest) {
        User user = null;
        String domain = null;
        if (isAuthenticatedRequest) {
            try {
                user = CommCareApplication.instance().getSession().getLoggedInUser();
            } catch (SessionUnavailableException sue) {
                throw new RuntimeException("Can't find user to make authenticated http request.");
            }
            domain = HiddenCommCarePreferences.getUserDomain();
        }
        return new Pair<>(user, domain);
    }

    private static String buildAppPassword(String password) {
        if (DeveloperPreferences.useObfuscatedPassword()) {
            return CredentialUtil.wrap(password);
        }
        return password;
    }

    private static String buildDomainUser(String username) {
        if (username != null && !username.contains("@")) {
            String domain = HiddenCommCarePreferences.getUserDomain();
            if (domain != null) {
                username += "@" + domain;
            }
        }
        return username;
    }

    private static String getCredential(User user, String domain) {
        final String username;
        if (domain != null) {
            username = user.getUsername() + "@" + domain;
        } else {
            username = user.getUsername();
        }
        final String password = user.getCachedPwd();
        return getCredential(username, password);
    }

    private static String getCredential(String username, String password) {
        if (username == null || password == null) {
            return null;
        } else {
            return okhttp3.Credentials.basic(username, password);
        }
    }
}
