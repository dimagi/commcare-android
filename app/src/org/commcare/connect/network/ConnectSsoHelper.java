package org.commcare.connect.network;

import android.content.Context;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.core.network.AuthInfo;
import org.commcare.util.LogTypes;
import org.commcare.utils.GlobalErrorUtil;
import org.commcare.utils.GlobalErrors;
import org.javarosa.core.services.Logger;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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


    public static void retrievePersonalIdToken(Context context, @NonNull ConnectUserRecord user, TokenCallback callback) {

        AuthInfo.TokenAuth connectToken = PersonalIdManager.getInstance().getConnectToken();
        if (connectToken != null) {
            callback.tokenRetrieved(connectToken);
            return;
        }

        new PersonalIdApiHandler<AuthInfo.TokenAuth>() {

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                if (errorCode == PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR) {
                    GlobalErrorUtil.triggerGlobalError(GlobalErrors.PERSONALID_LOST_CONFIGURATION_ERROR);
                } else {
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

    public static void retrieveHqSsoToken(Context context, @NonNull ConnectUserRecord user,
                                          @NonNull ConnectLinkedAppRecord appRecord, String hqUsername,
                                          boolean linkHqUser, TokenCallback callback) {

        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        //See if we already have a valid token
        AuthInfo.TokenAuth hqTokenAuth = PersonalIdManager.getInstance().getTokenCredentialsForApp(seatedAppId, hqUsername);
        if (hqTokenAuth != null) {
            callback.tokenRetrieved(hqTokenAuth);
            return;
        }

        //Need a new token, and may need to perform HQ-ConnectID linking
        if (linkHqUser || appRecord.getWorkerLinked()) {
            //  first get the connect token
            new PersonalIdApiHandler<AuthInfo.TokenAuth>() {
                @Override
                public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                    if (errorCode == PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR) {
                        callback.tokenRequestDenied();
                    } else {
                        callback.tokenUnavailable();
                    }
                }

                @Override
                public void onSuccess(AuthInfo.TokenAuth personalIdToken) {

                    if (!appRecord.getWorkerLinked()) {
                        // link hq user first and then retrieve hq token
                        ConnectSsoHelper.linkHqWorker(context, hqUsername, appRecord, personalIdToken.bearerToken, callback);
                    } else {
                        //  user already linked, get the hq sso token
                        ConnectSsoHelper.retrieveHqToken(context, hqUsername, personalIdToken.bearerToken, callback);
                    }

                }
            }.retrievePersonalIdToken(context, user);
        } else {
            callback.tokenUnavailable();
        }
    }

    private static void retrieveHqToken(Context context,
                                        String hqUsername,
                                        String personalIdToken,
                                        TokenCallback callback) {
        new PersonalIdApiHandler<AuthInfo.TokenAuth>() {
            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                if (errorCode == PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR) {
                    callback.tokenRequestDenied();
                } else {
                    callback.tokenUnavailable();
                }
            }

            @Override
            public void onSuccess(AuthInfo.TokenAuth hqToken) {
                callback.tokenRetrieved(hqToken);
            }
        }.retrieveHqToken(context, hqUsername, personalIdToken);
    }

    public static void discardTokens(Context context, String username) {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();

        Logger.log(LogTypes.TYPE_MAINTENANCE, "Clearing SSO tokens");

        if (username != null) {
            ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(context, seatedAppId, username);
            if (appRecord != null) {
                appRecord.clearHqToken();
                ConnectAppDatabaseUtil.storeApp(context, appRecord);
            }
        }

        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
        if (user != null) {
            user.clearConnectToken();
            ConnectUserDatabaseUtil.storeUser(context, user);
        }
    }

    public static void linkHqWorker(Context context,
                                    String hqUsername,
                                    ConnectLinkedAppRecord appRecord,
                                    String personalIdToken,
                                    TokenCallback callback) {
        new PersonalIdApiHandler<Boolean>() {

            @Override
            public void onFailure(@NonNull PersonalIdOrConnectApiErrorCodes errorCode, @Nullable Throwable t) {
                Logger.exception("Failed to link HQ worker", Objects.requireNonNullElseGet(t, () -> new Throwable("Failed to link HQ worker")));
                //  still try to get the Hq token as server sends failure if already linked
                ConnectSsoHelper.retrieveHqToken(context, hqUsername, personalIdToken, callback);
            }

            @Override
            public void onSuccess(Boolean succes) {
                ConnectSsoHelper.retrieveHqToken(context, hqUsername, personalIdToken, callback);
            }
        }.linkHqWorker(context, hqUsername, appRecord, personalIdToken);
    }
}
