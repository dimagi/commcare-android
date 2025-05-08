package org.commcare.connect.network;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.core.network.AuthInfo;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.lang.ref.WeakReference;

/**
 * Helper class for making SSO calls (both to ConnectID and HQ servers)
 *
 * @author dviggiano
 */
public class ConnectSsoHelper {
    public interface TokenCallback {
        void tokenRetrieved(AuthInfo.TokenAuth token);
        void tokenUnavailable();
        void tokenRequestDenied();
    }

    //Used for asynchronously retrieving HQ or SSO token
    private static class TokenTask extends AsyncTask<Void, Void, AuthInfo.TokenAuth> {
        private final WeakReference<Context> weakContext;
        private final ConnectUserRecord user;
        private final ConnectLinkedAppRecord appRecord;
        private final String hqUsername; //null for ConnectId
        private final boolean linkHqUser;
        final TokenCallback callback;
        private Exception caughtException;
        TokenTask(Context context, @NonNull ConnectUserRecord user, TokenCallback callback) {
            super();
            this.weakContext = new WeakReference<>(context);
            this.user = user;
            this.appRecord = null;
            this.hqUsername = null;
            this.linkHqUser = false;
            this.callback = callback;
        }

        TokenTask(Context context, @NonNull ConnectUserRecord user, ConnectLinkedAppRecord appRecord, String hqUsername, boolean linkHqUser, TokenCallback callback) {
            super();
            this.weakContext = new WeakReference<>(context);
            this.user = user;
            this.appRecord = appRecord;
            this.hqUsername = hqUsername;
            this.linkHqUser = linkHqUser;
            this.callback = callback;
        }

        @Override
        protected AuthInfo.TokenAuth doInBackground(Void... voids) {
            try {
                Context context = weakContext.get();
                if (hqUsername == null) {
                    return retrieveConnectIdTokenSync(context, user);
                }

                return retrieveHqSsoTokenSync(context, user, appRecord, hqUsername, linkHqUser);
            } catch(TokenUnavailableException | TokenDeniedException e) {
                caughtException = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(AuthInfo.TokenAuth token) {
            if(caughtException != null) {
                if(caughtException instanceof TokenUnavailableException) {
                    Logger.exception("Token unavailable", caughtException);
                    callback.tokenUnavailable();
                } else {
                    Logger.exception("Token request denied", caughtException);
                    callback.tokenRequestDenied();
                }
            } else {
                callback.tokenRetrieved(token);
            }
        }
    }

    public static void retrieveConnectIdTokenAsync(Context context, @NonNull ConnectUserRecord user, TokenCallback callback) {
        TokenTask task = new TokenTask(context, user, callback);

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void retrieveHqSsoTokenAsync(Context context, @NonNull ConnectUserRecord user,
                                               @NonNull ConnectLinkedAppRecord appRecord, String hqUsername,
                                               boolean linkHqUser, TokenCallback callback) {
        TokenTask task = new TokenTask(context, user, appRecord, hqUsername, linkHqUser, callback);

        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static AuthInfo.TokenAuth retrieveConnectIdTokenSync(Context context, @NonNull ConnectUserRecord user)
            throws TokenDeniedException, TokenUnavailableException {
        //See if we already have a valid token
        AuthInfo.TokenAuth connectToken = ConnectIDManager.getInstance().getConnectToken();
        if (connectToken != null) {
            return connectToken;
        }

        return ApiConnectId.retrieveConnectIdTokenSync(context, user);
    }

    public static AuthInfo.TokenAuth retrieveHqSsoTokenSync(Context context, @NonNull ConnectUserRecord user, @NonNull ConnectLinkedAppRecord appRecord, String hqUsername, boolean performLink) throws TokenDeniedException, TokenUnavailableException {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        //See if we already have a valid token
        AuthInfo.TokenAuth hqTokenAuth = ConnectIDManager.getInstance().getTokenCredentialsForApp(seatedAppId, hqUsername);
        if(hqTokenAuth != null) {
            return hqTokenAuth;
        }

        //Need a new token, and may need to perform HQ-ConnectID linking
        if (performLink || appRecord.getWorkerLinked()) {
            //First get a valid ConnectId token
            AuthInfo.TokenAuth connectIdToken = retrieveConnectIdTokenSync(context, user);

            if (!appRecord.getWorkerLinked()) {
                //Link user if necessary
                ApiConnectId.linkHqWorker(context, hqUsername, appRecord, connectIdToken.bearerToken);
            }

            //Retrieve HQ token
            return ApiConnectId.retrieveHqTokenSync(context, hqUsername, connectIdToken.bearerToken);
        }

        throw new TokenUnavailableException();
    }

    public static void discardTokens(Context context, String username) {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        Logger.log(LogTypes.TYPE_MAINTENANCE, "Clearing SSO tokens");

        if(username != null) {
            ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, seatedAppId, username);
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
