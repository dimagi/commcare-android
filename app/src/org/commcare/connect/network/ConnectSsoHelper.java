package org.commcare.connect.network;

import android.content.Context;
import android.os.AsyncTask;

import org.commcare.CommCareApplication;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.core.network.AuthInfo;

import java.lang.ref.WeakReference;

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
                return ApiConnectId.retrieveConnectIdTokenSync(context);
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
            //First get a valid ConnectId token
            AuthInfo.TokenAuth connectIdToken = ApiConnectId.retrieveConnectIdTokenSync(context);

            //If we can't get a valid Connect token there's no point continuing
            if (connectIdToken != null) {
                if(!appRecord.getWorkerLinked()) {
                    //Link user if necessary
                    ApiConnectId.linkHqWorker(context, hqUsername,
                            appRecord.getPassword(), connectIdToken.bearerToken);
                }

                //Retrieve HQ token
                hqTokenAuth = ApiConnectId.retrieveHqTokenApi(context, hqUsername, connectIdToken.bearerToken);
            }
        }

        return hqTokenAuth;
    }

    public static void retrieveConnectTokenAsync(Context context, TokenCallback callback) {
        TokenTask task = new TokenTask(context, null, false, callback);

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
