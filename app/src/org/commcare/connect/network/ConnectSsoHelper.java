package org.commcare.connect.network;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.core.network.AuthInfo;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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


    public static void retrieveConnectIdTokenAsync(Context context, @NonNull ConnectUserRecord user, TokenCallback callback) {

        AuthInfo.TokenAuth connectToken = PersonalIdManager.getInstance().getConnectToken();
        if (connectToken != null) {
            callback.tokenRetrieved(connectToken);
            return;
        }

        new PersonalIdApiHandler<AuthInfo.TokenAuth>() {

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                if(errorCode==PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR){
                    callback.tokenRequestDenied();
                }else{
                    callback.tokenUnavailable();
                }

            }

            @Override
            public void onSuccess(AuthInfo.TokenAuth tokenAuth) {
                ConnectUserDatabaseUtil.storeUser(context, user);
                callback.tokenRetrieved(tokenAuth);
            }
        }.retrievePersonalIdToken(context, user);
    }

    public static void retrieveHqSsoTokenAsync(Context context, @NonNull ConnectUserRecord user,
                                               @NonNull ConnectLinkedAppRecord appRecord, String hqUsername,
                                               boolean linkHqUser, TokenCallback callback) {

        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        //See if we already have a valid token
        AuthInfo.TokenAuth hqTokenAuth = PersonalIdManager.getInstance().getTokenCredentialsForApp(seatedAppId, hqUsername);
        if(hqTokenAuth != null) {
            callback.tokenRetrieved(hqTokenAuth);
            return;
        }

        //Need a new token, and may need to perform HQ-ConnectID linking
        if (linkHqUser || appRecord.getWorkerLinked()) {
            //  first get the connect token
            new PersonalIdApiHandler<AuthInfo.TokenAuth>() {
                @Override
                public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                    if(errorCode==PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR){
                        callback.tokenRequestDenied();
                    }else{
                        callback.tokenUnavailable();
                    }
                }
                @Override
                public void onSuccess(AuthInfo.TokenAuth connectIdToken) {
                    linkHqUser(context,appRecord,hqUsername,connectIdToken);
                    //  now get the hq sso token
                    ConnectSsoHelper.retrieveHqToken(context,hqUsername,connectIdToken,callback);
                }
            }.retrievePersonalIdToken(context, user);
        }else{
            callback.tokenUnavailable();
        }
    }

    private static void linkHqUser(Context context,
                                   @NonNull ConnectLinkedAppRecord appRecord,
                                   String hqUsername,
                                   AuthInfo.TokenAuth connectIdToken){
        //  link user if not already
        if (!appRecord.getWorkerLinked()) {
            ConnectSsoHelper.linkHqWorker(context, hqUsername, appRecord, connectIdToken.bearerToken);
        }
    }

    private static void retrieveHqToken(Context context,
                                        String hqUsername,
                                        AuthInfo.TokenAuth connectIdToken,
                                        TokenCallback callback){
        new PersonalIdApiHandler<AuthInfo.TokenAuth>() {
            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                if(errorCode==PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR){
                    callback.tokenRequestDenied();
                }else{
                    callback.tokenUnavailable();
                }
            }
            @Override
            public void onSuccess(AuthInfo.TokenAuth hqToken) {
                callback.tokenRetrieved(hqToken);
            }
        }.retrieveHqToken(context, hqUsername,connectIdToken.bearerToken);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static AuthInfo.TokenAuth retrieveConnectIdTokenSync(Context context, @NonNull ConnectUserRecord user)
            throws TokenDeniedException, TokenUnavailableException {
        //See if we already have a valid token
        AuthInfo.TokenAuth connectToken = PersonalIdManager.getInstance().getConnectToken();
        if (connectToken != null) {
            return connectToken;
        }


        CompletableFuture<AuthInfo.TokenAuth> completableFuture = new CompletableFuture<>();
        retrieveConnectIdTokenAsync(context, user, new TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                completableFuture.complete(token);
            }

            @Override
            public void tokenUnavailable() {
                completableFuture.completeExceptionally(new TokenUnavailableException());
            }

            @Override
            public void tokenRequestDenied() {
                completableFuture.completeExceptionally(new TokenDeniedException());
            }
        });

        try {
            return completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            if(e.getCause() instanceof TokenDeniedException){
                throw new TokenDeniedException();
            }else{
                throw new TokenUnavailableException();
            }
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static AuthInfo.TokenAuth retrieveHqSsoTokenSync(Context context, @NonNull ConnectUserRecord user, @NonNull ConnectLinkedAppRecord appRecord, String hqUsername, boolean performLink) throws
            TokenDeniedException, TokenUnavailableException {
        CompletableFuture<AuthInfo.TokenAuth> completableFuture = new CompletableFuture<>();
        retrieveHqSsoTokenAsync(context, user,appRecord,hqUsername,performLink, new TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                completableFuture.complete(token);
            }

            @Override
            public void tokenUnavailable() {
                completableFuture.completeExceptionally(new TokenUnavailableException());
            }

            @Override
            public void tokenRequestDenied() {
                completableFuture.completeExceptionally(new TokenDeniedException());
            }
        });

        try {
            return completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            if(e.getCause() instanceof TokenDeniedException){
                throw new TokenDeniedException();
            }else{
                throw new TokenUnavailableException();
            }
        }
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


    public static void linkHqWorker(Context context, String hqUsername, ConnectLinkedAppRecord appRecord, String connectToken) {
        new PersonalIdApiHandler<Boolean>() {

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                Logger.exception("Failed to link HQ worker", Objects.requireNonNullElseGet(t, () -> new Throwable("Failed to link HQ worker")));
            }

            @Override
            public void onSuccess(Boolean succes) {

            }
        }.linkHqWorker(context, hqUsername,appRecord,connectToken);
    }
}
