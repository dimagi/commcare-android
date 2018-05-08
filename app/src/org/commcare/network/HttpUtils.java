package org.commcare.network;

import org.commcare.CommCareApplication;
import org.commcare.core.network.AuthInfo;
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

    public static String getCredential(AuthInfo authInfo) {
        if (authInfo instanceof AuthInfo.ProvidedAuth) {
            return getCredential(buildDomainUser(authInfo.username), buildAppPassword(authInfo.password));
        } else if (authInfo instanceof AuthInfo.CurrentAuth) {
            Pair<User, String> userAndDomain = getUserAndDomain();
            return getCredential(userAndDomain.first, userAndDomain.second);
        } else {
            throw new IllegalArgumentException("Cannot get credential with NoAuth authInfo arg");
        }
    }

    private static Pair<User, String> getUserAndDomain() {
        try {
            User user = CommCareApplication.instance().getSession().getLoggedInUser();
            return new Pair<>(user, HiddenPreferences.getUserDomain());
        } catch (SessionUnavailableException sue) {
            throw new RuntimeException("Can't find user to make authenticated http request.");
        }
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
