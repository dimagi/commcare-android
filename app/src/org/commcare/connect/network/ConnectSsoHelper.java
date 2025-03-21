package org.commcare.connect.network;

import android.content.Context;
import android.os.AsyncTask;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.core.network.AuthInfo;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.lang.ref.WeakReference;
import java.net.MalformedURLException;

/**
 * Helper class for making SSO calls (both to ConnectID and HQ servers)
 *
 * @author dviggiano
 */
public class ConnectSsoHelper {
    public interface TokenCallback {
        void tokenRetrieved(AuthInfo.TokenAuth token);
    }

    //Used for asynchronously retrieving HQ or SSO token
    private static class TokenTask extends AsyncTask<Void, Void, AuthInfo.TokenAuth> {
        private final WeakReference<Context> weakContext;
        private final String hqUsername; //null for ConnectId
        private final boolean linkHqUser;
        final TokenCallback callback;
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
                return retrieveConnectIdTokenSync(context);
            }

            return retrieveHqSsoTokenSync(context, hqUsername, linkHqUser);
        }

        @Override
        protected void onPostExecute(AuthInfo.TokenAuth token) {
            callback.tokenRetrieved(token);
        }
    }

    public static void retrieveConnectIdTokenAsync(Context context, TokenCallback callback) {
        TokenTask task = new TokenTask(context, null, false, callback);

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void retrieveHqSsoTokenAsync(Context context, String hqUsername, boolean linkHqUser, TokenCallback callback) {
        TokenTask task = new TokenTask(context, hqUsername, linkHqUser, callback);

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static AuthInfo.TokenAuth retrieveConnectIdTokenSync(Context context) {
        if (!ConnectManager.isConnectIdConfigured()) {
            return null;
        }

        AuthInfo.TokenAuth connectToken = ConnectManager.getConnectToken();
        if (connectToken != null) {
            return connectToken;
        }

        return ApiConnectId.retrieveConnectIdTokenSync(context);
    }

    public static AuthInfo.TokenAuth retrieveHqSsoTokenSync(Context context, String hqUsername, boolean performLink) {
        if (!ConnectManager.isConnectIdConfigured()) {
            return null;
        }

        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getAppData(context, seatedAppId, hqUsername);
        if (appRecord == null) {
            return null;
        }

        //See if we already have a valid token
        AuthInfo.TokenAuth hqTokenAuth = ConnectManager.getTokenCredentialsForApp(seatedAppId, hqUsername);
        if (hqTokenAuth == null && (performLink || appRecord.getWorkerLinked())) {
            //First get a valid ConnectId token
            AuthInfo.TokenAuth connectIdToken = retrieveConnectIdTokenSync(context);

            //If we can't get a valid Connect token there's no point continuing
            if (connectIdToken != null) {
                if(!appRecord.getWorkerLinked()) {
                    //Link user if necessary
                    ApiConnectId.linkHqWorker(context, hqUsername, appRecord, connectIdToken.bearerToken);
                }

                //Retrieve HQ token
                try {
                    hqTokenAuth = ApiConnectId.retrieveHqTokenSync(context, hqUsername, connectIdToken.bearerToken);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return hqTokenAuth;
    }

    public static void discardTokens(Context context, String username) {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        Logger.log(LogTypes.TYPE_MAINTENANCE, "Clearing SSO tokens");

        if(username != null) {
            ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getAppData(context, seatedAppId, username);
            if (appRecord != null) {
                appRecord.clearHqToken();
                ConnectAppDatabaseUtil.storeApp(context, appRecord);
            }
        }

        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
        if(user != null) {
            user.clearConnectToken();
            ConnectUserDatabaseUtil.storeUser(context, user);
        }
    }
}
