package org.commcare.network;

import android.content.SharedPreferences;

import org.commcare.CommCareApplication;
import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.CommCarePreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.utils.CredentialUtil;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.model.User;

import java.util.List;

import javax.annotation.Nullable;

import retrofit2.Response;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpUtils {

    public static String getCredential(@Nullable Pair<String, String> usernameAndPasswordToAuthWith) {
        String credential;
        if (usernameAndPasswordToAuthWith == null) {
            // User already logged in
            Pair<User, String> userAndDomain = getUserAndDomain(true);
            credential = ModernHttpRequester.getCredential(userAndDomain.first, userAndDomain.second);
        } else {
            credential = ModernHttpRequester.getCredential(
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
            domain = CommCarePreferences.getUserDomain();
        }
        return new Pair<>(user, domain);
    }

    public static String buildAppPassword(String password) {
        if (DeveloperPreferences.useObfuscatedPassword()) {
            return CredentialUtil.wrap(password);
        }
        return password;
    }

    public static String buildDomainUser(String username) {
        if (username != null && !username.contains("@")) {
            String domain = CommCarePreferences.getUserDomain();
            if (domain != null) {
                username += "@" + domain;
            }
        }
        return username;
    }
}
