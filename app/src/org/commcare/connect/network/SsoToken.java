package org.commcare.connect.network;

import org.commcare.connect.ConnectConstants;
import org.javarosa.core.io.StreamsUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class SsoToken {
    public String token;
    public Date expiration;

    public SsoToken(String token, Date expiration) {
        this.token = token;
        this.expiration = expiration;
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
        int seconds = json.has(key) ? json.getInt(key) : 0;
        expiration.setTime(expiration.getTime() + ((long)seconds * 1000));

        return new SsoToken(token, expiration);
    }
}
