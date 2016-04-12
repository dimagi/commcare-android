package org.commcare.network;

import org.commcare.utils.GlobalConstants;
import org.javarosa.core.model.User;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpGenerator {
    private final boolean isPostRequest;

    public ModernHttpGenerator(User user) {
        this(user.getUsername(), user.getCachedPwd(), user.getUserType());
    }

    public ModernHttpGenerator(String username, String password) {
        this(username, password, null);
    }

    private ModernHttpGenerator(final String username, final String password, String userType) {
        isPostRequest = false;
        if (username == null || password == null || User.TYPE_DEMO.equals(userType)) {
            // clear any prior set authenticator to make unauthed requests
            Authenticator.setDefault(null);
        } else {
            // make authenticated requests
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            HttpRequestGenerator.buildDomainUser(username),
                            password.toCharArray());
                }
            });
        }
    }

    public static ModernHttpGenerator buildNoAuthGenerator() {
        return new ModernHttpGenerator(null, null, null);
    }

    private InputStream makeRequest(URL url) throws IOException {
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        setupGetConnection(con);
        con.connect();

        processResponse(con);
        return con.getInputStream();
    }

    private void processResponse(HttpURLConnection con) throws IOException {
        int responseCode = con.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            // 200s: success
        } else if (responseCode == 301) {
            // 301 : redirect
        } else if (responseCode == 401) {
            // 401 : auth failed
        } else if (responseCode == 412) {
            // 412 : recovery?
        } else if (responseCode == 500) {
            // 500 : server error
        } else {

        }
    }

    private void setupGetConnection(HttpURLConnection con) throws IOException {
        con.setConnectTimeout(GlobalConstants.CONNECTION_TIMEOUT);
        con.setReadTimeout(GlobalConstants.CONNECTION_SO_TIMEOUT);
        con.setRequestMethod(isPostRequest ? "POST" : "GET");
        con.setDoInput(true);
        con.setInstanceFollowRedirects(true);
    }
}
