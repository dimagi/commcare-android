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
import org.javarosa.xml.ElementParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.json.JSONException;
import org.json.JSONObject;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Credentials;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class HttpUtils {
    public static String getCredential(AuthInfo authInfo) {
        if (authInfo instanceof AuthInfo.ProvidedAuth) {
            String username = ((AuthInfo.ProvidedAuth)authInfo).wrapDomain ? buildDomainUser(authInfo.username) : authInfo.username;
            return getCredential(username, authInfo.password);
        } else if (authInfo instanceof AuthInfo.TokenAuth) {
            return getCredential(((AuthInfo.TokenAuth)authInfo).bearerToken);
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

    private static String getCredential(String bearerToken) {
        if (bearerToken == null) {
            return null;
        } else {
            return "Bearer " + bearerToken;
        }
    }

    public static String parseUserVisibleError(Response<ResponseBody> response) {
        String message;
        String responseStr = null;
        try {
            responseStr = response.errorBody().string();

            Map<String, String> errorBodyKeyValuePairs = null;
            if (response.errorBody().contentType().toString().contains("application/json")) {
                errorBodyKeyValuePairs = parseJsonErrorResponseBody(responseStr);
            } else {
                errorBodyKeyValuePairs = parseXmlErrorResponseBody(responseStr);
            }
            message = Localization.getWithDefault(
                    errorBodyKeyValuePairs.get("error"),
                    errorBodyKeyValuePairs.get("default_response"));
        } catch (JSONException | IOException | InvalidStructureException | UnfullfilledRequirementsException
                 | XmlPullParserException e) {
            message = responseStr != null ? responseStr : "Unknown issue";
        }
        return message;
    }

    /* *
     * Parses the JSON-formatted error response body from HQ. The response body is expected to be in the format:
     * {
     *   "error" : "error.message.key",
     *   "default_response" : "Default message in English"
     *  }
     * Returns a map containing these key-value pairs.
     */
    public static Map<String, String> parseJsonErrorResponseBody(String responseStr) throws JSONException {
        Map<String, String> map = new HashMap<>();
        JSONObject jsonObject = new JSONObject(responseStr);
        if (jsonObject != null) {
            map.put("error", jsonObject.getString("error"));
            map.put("default_response", jsonObject.getString("default_response"));
        }
        return map;
    }

    /* *
     * Parses XML-formatted error response body from HQ. The response body is expected to be in the format:
     * <OpenRosaResponse xmlns="http://openrosa.org/http/response">
     *     <message nature="ota_restore_error">
     *       <error>
     *         error.message.string.key
     *       </error>
     *       <default_response>
     *         Default message in English
     *       </default_response>
     *     </message>
     *   </OpenRosaResponse>
     * Returns a map containing the relevant key-value pairs.
     */
    public static Map<String, String> parseXmlErrorResponseBody(String responseStr)
            throws IOException, InvalidStructureException, UnfullfilledRequirementsException,
            XmlPullParserException {

        KXmlParser baseParser = ElementParser.instantiateParser(
                new ByteArrayInputStream(responseStr.getBytes(StandardCharsets.UTF_8)));
        ElementParser<Map<String, String>> responseParser = new ElementParser<>(baseParser) {
            @Override
            public Map<String, String> parse() throws InvalidStructureException, IOException,
                    XmlPullParserException {
                Map<String, String> map = new HashMap<>();
                checkNode("OpenRosaResponse");
                nextTag("message");
                nextTag("error");
                map.put("error", parser.nextText());
                nextTag("default_response");
                map.put("default_response", parser.nextText());
                return map;
            }
        };
        return responseParser.parse();
    }
}
