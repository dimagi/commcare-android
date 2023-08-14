package org.commcare.activities.connect;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;

public class ConnectIDSSOHelper {
    public static AuthInfo.TokenAuth acquireSSOTokenSync(Context context) {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        String hqUser;
        try {
            hqUser = CommCareApplication.instance().getRecordForCurrentUser().getUsername();
        } catch(Exception e) {
            //No token if no session
            return null;
        }

        ConnectLinkedAppRecord appRecord = ConnectIDDatabaseHelper.getAppData(context, seatedAppId, hqUser);
        if(appRecord == null) {
            return null;
        }

        //See if we already have a valid token
        AuthInfo.TokenAuth hqTokenAuth = ConnectIDManager.getTokenCredentialsForApp(seatedAppId, hqUser);
        if(hqTokenAuth == null) {
            //First get a valid Connect token
            AuthInfo.TokenAuth connectToken = ConnectIDManager.getConnectToken();
            if(connectToken == null) {
                //Retrieve a new connect token
                connectToken = retrieveConnectToken(context);
            }

            if(connectToken == null) {
                //If we can't get a valid Connect token there's no point continuing
                return null;
            }

            //Link user if necessary
            linkHQWorker(context, hqUser, appRecord.getPassword(), connectToken.bearerToken);

            //Retrieve HQ token
            hqTokenAuth = retrieveHQToken(context, hqUser, connectToken.bearerToken);
        }

        return hqTokenAuth;
    }

    private static AuthInfo.TokenAuth retrieveConnectToken(Context context) {
        ConnectUserRecord user = ConnectIDDatabaseHelper.getUser(context);

        HashMap<String, String> params = new HashMap<>();
        params.put("client_id", "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa");
        params.put("scope", "openid");
        params.put("grant_type", "password");
        params.put("username", user.getUserID());
        params.put("password", user.getPassword());

        String url = context.getString(R.string.ConnectTokenURL);

        ConnectIDNetworkHelper.PostResult postResult = ConnectIDNetworkHelper.postSync(context, url, new AuthInfo.NoAuth(), params, true);
        if(postResult.responseCode == 200) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(postResult.responseStream));
                postResult.responseStream.close();
                JSONObject json = new JSONObject(responseAsString);
                String key = ConnectIDConstants.CONNECT_KEY_TOKEN;
                if (json.has(key)) {
                    String token = json.getString(key);
                    Date expiration = new Date();
                    key = ConnectIDConstants.CONNECT_KEY_EXPIRES;
                    int seconds = json.has(key) ? json.getInt(key) : 0;
                    expiration.setTime(expiration.getTime() + ((long)seconds * 1000));
                    user.updateConnectToken(token, expiration);
                    ConnectIDDatabaseHelper.storeUser(context, user);

                    return new AuthInfo.TokenAuth(token);
                }
            } catch (IOException | JSONException e) {
                Logger.exception("Parsing return from Connect OIDC call", e);
            }
        }

        return null;
    }

    private static void linkHQWorker(Context context, String hqUsername, String hqPassword, String connectToken) {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectLinkedAppRecord appRecord = ConnectIDDatabaseHelper.getAppData(context, seatedAppId, hqUsername);
        if(appRecord != null && !appRecord.getWorkerLinked()) {
            HashMap<String, String> params = new HashMap<>();
            params.put("token", connectToken);

            String url = ServerUrls.getKeyServer().replace("phone/keys/", "settings/users/commcare/link_connectid_user/");

            try {
                ConnectIDNetworkHelper.PostResult postResult = ConnectIDNetworkHelper.postSync(context, url, new AuthInfo.ProvidedAuth(hqUsername, hqPassword), params, true);
                if (postResult.e == null && postResult.responseCode == 200) {
                    postResult.responseStream.close();

                    //Remember that we linked the user successfully
                    appRecord.setWorkerLinked(true);
                    ConnectIDDatabaseHelper.storeApp(context, appRecord);
                }
            } catch (IOException e) {
                //Don't care for now
            }
        }
    }

    private static AuthInfo.TokenAuth retrieveHQToken(Context context, String hqUsername, String connectToken) {
        HashMap<String, String> params = new HashMap<>();
        params.put("client_id", "4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV");
        params.put("scope", "sync");
        params.put("grant_type", "password");
        params.put("username", hqUsername + "@" + HiddenPreferences.getUserDomain());
        params.put("password", connectToken);

        String host = "";
        try {
            host = (new URL(ServerUrls.getKeyServer())).getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        String url = "https://" + host + "/oauth/token/";

        ConnectIDNetworkHelper.PostResult postResult = ConnectIDNetworkHelper.postSync(context, url, new AuthInfo.NoAuth(), params, true);
        if(postResult.responseCode == 200) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(postResult.responseStream));
                JSONObject json = new JSONObject(responseAsString);
                String key = ConnectIDConstants.CONNECT_KEY_TOKEN;
                if (json.has(key)) {
                    String token = json.getString(key);
                    Date expiration = new Date();
                    key = ConnectIDConstants.CONNECT_KEY_EXPIRES;
                    int seconds = json.has(key) ? json.getInt(key) : 0;
                    expiration.setTime(expiration.getTime() + ((long)seconds * 1000));

                    String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                    ConnectIDDatabaseHelper.storeHQToken(context, seatedAppId, hqUsername, token, expiration);

                    return new AuthInfo.TokenAuth(token);
                }
            } catch (IOException | JSONException e) {
                Logger.exception("Parsing return from HQ OIDC call", e);
            }
        }

        return null;
    }

}
