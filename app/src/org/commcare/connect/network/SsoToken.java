package org.commcare.connect.network;

import org.commcare.connect.ConnectConstants;
import org.javarosa.core.io.StreamsUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class SsoToken {

    private final String token;

    private final Date expiration;

    public SsoToken(String token, Date expiration) {
        if (token == null || expiration == null) {
            throw new IllegalArgumentException("Token and expiration must not be null");
        }
        if (token.isEmpty()) {
            throw new IllegalArgumentException("Token must not be empty");
        }
        this.token = token;
        this.expiration = new Date(expiration.getTime());
    }

    public static SsoToken fromResponseStream(InputStream stream) throws IOException, JSONException {
        String responseAsString = new String(StreamsUtil.inputStreamToByteArray(
                stream));
        JSONObject json = new JSONObject(responseAsString);
        String key = ConnectConstants.CONNECT_KEY_TOKEN;

        if (!json.has(key)) {
            throw new RuntimeException("SSO API response missing access token");
        }
        String token = json.getString(key);
        Date expiration = new Date();
        key = ConnectConstants.CONNECT_KEY_EXPIRES;
        long seconds = json.has(key) ? json.getLong(key) : 0L;
        expiration.setTime(expiration.getTime() + ((long)seconds * 1000));

        return new SsoToken(token, expiration);
    }
    public String getToken() {
        return token;
    }
    public Date getExpiration() {
        return expiration;
    }
}
