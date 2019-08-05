package org.commcare.network;

import org.commcare.CommCareApplication;
import org.commcare.core.network.AuthInfo;
import org.commcare.modern.util.Pair;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.utils.CredentialUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.StringUtils;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import javax.annotation.Nullable;

import okhttp3.Credentials;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpUtils {

    public static String getCredential(AuthInfo authInfo) {
        if (authInfo instanceof AuthInfo.ProvidedAuth) {
            return getCredential(buildDomainUser(authInfo.username), authInfo.password);
        } else if (authInfo instanceof AuthInfo.CurrentAuth) {
            // use the logged in user
            User user = getUser();
            return getCredential(buildDomainUser(user.getUsername()), user.getCachedPwd());
        } else {
            throw new IllegalArgumentException("Cannot get credential with NoAuth authInfo arg");
        }
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
            return Credentials.basic(username, buildAppPassword(password));
        }
    }

    public static String parseUserVisibleError(Response<ResponseBody> response) {
        String message;
        String responseStr = null;
        try {
            responseStr = response.errorBody().string();
            JSONObject errorKeyAndDefault = new JSONObject(responseStr);
            message = Localization.getWithDefault(
                    errorKeyAndDefault.getString("error"),
                    errorKeyAndDefault.getString("default_response"));
        } catch (JSONException | IOException e) {
            message = responseStr != null ? responseStr : "Unknown issue";
        }
        return message;
    }
}
