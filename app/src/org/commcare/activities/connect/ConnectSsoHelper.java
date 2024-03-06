package org.commcare.activities.connect;

import android.content.Context;
import android.os.AsyncTask;

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
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;

/**
 * Helper class for making SSO calls (both to ConnectID and HQ servers)
 *
 * @author dviggiano
 */
public class ConnectSsoHelper {
    public interface TokenCallback {
        void tokenRetrieved(AuthInfo.TokenAuth token);
    }

    //Used for aynchronously retrieving HQ or SSO token
    private static class TokenTask extends AsyncTask<Void, Void, AuthInfo.TokenAuth> {
        private final WeakReference<Context> weakContext;
        private final String hqUsername; //null for Connect
        private final boolean linkHqUser;
        TokenCallback callback;
        TokenTask(Context context, String hqUsername, boolean linkHqUser, TokenCallback callback) {
            super();
            this.weakContext = new WeakReference<>(context);
            this.hqUsername = hqUsername;
            this.linkHqUser = linkHqUser;
            this.callback = callback;
        }

        @Override
        protected AuthInfo.TokenAuth doInBackground(Void... voids) {
            Context context = weakContext.get();
            if(hqUsername == null) {
                return retrieveConnectTokenSync(context);
            }

            return retrieveHqSsoTokenSync(context, hqUsername, linkHqUser);
        }

        @Override
        protected void onPostExecute(AuthInfo.TokenAuth token) {
            callback.tokenRetrieved(token);
        }
    }

    public static void retrieveHqSsoTokenAsync(Context context, String hqUsername, boolean linkHqUser, TokenCallback callback) {
        TokenTask task = new TokenTask(context, hqUsername, linkHqUser, callback);

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static AuthInfo.TokenAuth retrieveHqSsoTokenSync(Context context, String hqUsername, boolean performLink) {
        if (!ConnectManager.isUnlocked()) {
            return null;
        }

        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        ConnectLinkedAppRecord appRecord = ConnectDatabaseHelper.getAppData(context, seatedAppId, hqUsername);
        if (appRecord == null) {
            return null;
        }

        //See if we already have a valid token
        AuthInfo.TokenAuth hqTokenAuth = ConnectManager.getTokenCredentialsForApp(seatedAppId, hqUsername);
        if (hqTokenAuth == null && (performLink || appRecord.getWorkerLinked())) {
            //First get a valid Connect token
            AuthInfo.TokenAuth connectToken = retrieveConnectTokenSync(context);

            //If we can't get a valid Connect token there's no point continuing
            if (connectToken != null) {
                if(!appRecord.getWorkerLinked()) {
                    //Link user if necessary
                    linkHqWorker(context, hqUsername, appRecord.getPassword(), connectToken.bearerToken);
                }

                //Retrieve HQ token
                hqTokenAuth = retrieveHqTokenApi(context, hqUsername, connectToken.bearerToken);
            }
        }

        return hqTokenAuth;
    }

    public static void retrieveConnectTokenAsync(Context context, TokenCallback callback) {
        TokenTask task = new TokenTask(context, null, false, callback);

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static AuthInfo.TokenAuth retrieveConnectTokenSync(Context context) {
        AuthInfo.TokenAuth connectToken = ConnectManager.getConnectToken();
        if(connectToken != null) {
            return connectToken;
        }

        ConnectUserRecord user = ConnectDatabaseHelper.getUser(context);

        HashMap<String, String> params = new HashMap<>();
        params.put("client_id", "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa");
        params.put("scope", "openid");
        params.put("grant_type", "password");
        params.put("username", user.getUserId());
        params.put("password", user.getPassword());

        String url = context.getString(R.string.ConnectTokenURL);

        ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                new AuthInfo.NoAuth(), params, true);
        if (postResult.responseCode == 200) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(
                        postResult.responseStream));
                postResult.responseStream.close();
                JSONObject json = new JSONObject(responseAsString);
                String key = ConnectConstants.CONNECT_KEY_TOKEN;
                if (json.has(key)) {
                    String token = json.getString(key);
                    Date expiration = new Date();
                    key = ConnectConstants.CONNECT_KEY_EXPIRES;
                    int seconds = json.has(key) ? json.getInt(key) : 0;
                    expiration.setTime(expiration.getTime() + ((long)seconds * 1000));
                    user.updateConnectToken(token, expiration);
                    ConnectDatabaseHelper.storeUser(context, user);

                    return new AuthInfo.TokenAuth(token);
                }
            } catch (IOException | JSONException e) {
                Logger.exception("Parsing return from Connect OIDC call", e);
            }
        }

        return null;
    }

    private static void linkHqWorker(Context context, String hqUsername, String hqPassword, String connectToken) {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectLinkedAppRecord appRecord = ConnectDatabaseHelper.getAppData(context, seatedAppId, hqUsername);
        if (appRecord != null && !appRecord.getWorkerLinked()) {
            HashMap<String, String> params = new HashMap<>();
            params.put("token", connectToken);

            String url = ServerUrls.getKeyServer().replace("phone/keys/", 
                    "settings/users/commcare/link_connectid_user/");

            try {
                ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                        new AuthInfo.ProvidedAuth(hqUsername, hqPassword), params, true);
                if (postResult.e == null && postResult.responseCode == 200) {
                    postResult.responseStream.close();

                    //Remember that we linked the user successfully
                    appRecord.setWorkerLinked(true);
                    ConnectDatabaseHelper.storeApp(context, appRecord);
                }
            } catch (IOException e) {
                //Don't care for now
            }
        }
    }

    private static AuthInfo.TokenAuth retrieveHqTokenApi(Context context, String hqUsername, String connectToken) {
        HashMap<String, String> params = new HashMap<>();
        params.put("client_id", "4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV");
        params.put("scope", "mobile_access sync");
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

        ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                new AuthInfo.NoAuth(), params, true);
        if (postResult.responseCode == 200) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(
                        postResult.responseStream));
                JSONObject json = new JSONObject(responseAsString);
                String key = ConnectConstants.CONNECT_KEY_TOKEN;
                if (json.has(key)) {
                    String token = json.getString(key);
                    Date expiration = new Date();
                    key = ConnectConstants.CONNECT_KEY_EXPIRES;
                    int seconds = json.has(key) ? json.getInt(key) : 0;
                    expiration.setTime(expiration.getTime() + ((long)seconds * 1000));

                    String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                    ConnectDatabaseHelper.storeHqToken(context, seatedAppId, hqUsername, token, expiration);

                    return new AuthInfo.TokenAuth(token);
                }
            } catch (IOException | JSONException e) {
                Logger.exception("Parsing return from HQ OIDC call", e);
            }
        }

        return null;
    }
}
