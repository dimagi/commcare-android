package org.commcare.connect.network;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.base.BaseApi;
import org.commcare.connect.network.connectId.PersonalIdApiClient;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.network.HttpUtils;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.util.LogTypes;
import org.commcare.utils.FirebaseMessagingUtil;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;


public class ApiPersonalId {
    private static final String API_VERSION_NONE = null;
    public static final String API_VERSION_PERSONAL_ID = "2.0";
    private static final int NETWORK_ACTIVITY_ID = 7000;
    private static final String HQ_CLIENT_ID = "4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV";
    private static final String CONNECT_CLIENT_ID = "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa";


    public static ConnectNetworkHelper.PostResult makeHeartbeatRequestSync(Context context, AuthInfo.TokenAuth auth) {
        String url = PersonalIdApiClient.BASE_URL + context.getString(R.string.ConnectHeartbeatURL);
        HashMap<String, Object> params = new HashMap<>();
        String token = FirebaseMessagingUtil.getFCMToken();
        if (token != null) {
            params.put("fcm_token", token);
            boolean useFormEncoding = true;
            return ConnectNetworkHelper.postSync(context, url, API_VERSION_PERSONAL_ID, auth, params,
                    useFormEncoding, true);
        }

        return new ConnectNetworkHelper.PostResult(-1, null, null);
    }

    public static AuthInfo.TokenAuth retrieveConnectIdTokenSync(Context context, @NonNull ConnectUserRecord user)
            throws TokenDeniedException, TokenUnavailableException {
        HashMap<String, Object> params = new HashMap<>();
        params.put("client_id", "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa");
        params.put("scope", "openid");
        params.put("grant_type", "password");
        params.put("username", user.getUserId());
        params.put("password", user.getPassword());

        String url = PersonalIdApiClient.BASE_URL + context.getString(R.string.ConnectTokenURL);

        ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                API_VERSION_PERSONAL_ID, new AuthInfo.NoAuth(), params, true, false);
        Logger.log(LogTypes.TYPE_MAINTENANCE, "Connect Token Post Result " + postResult.responseCode);
        if (postResult.responseCode >= 200 && postResult.responseCode < 300) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(
                        postResult.responseStream));
                postResult.responseStream.close();
                JSONObject json = new JSONObject(responseAsString);
                String key = ConnectConstants.CONNECT_KEY_TOKEN;
                String token = json.getString(key);
                Date expiration = new Date();
                key = ConnectConstants.CONNECT_KEY_EXPIRES;
                int seconds = json.has(key) ? json.getInt(key) : 0;
                expiration.setTime(expiration.getTime() + ((long) seconds * 1000));
                user.updateConnectToken(token, expiration);
                ConnectUserDatabaseUtil.storeUser(context, user);

                return new AuthInfo.TokenAuth(token);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                Logger.exception("Parsing return from ConnectID token call", e);
            }
        } else if (postResult.responseCode == 400) {
            Logger.exception("Token Request Denied", new Throwable("Encountered 400 while retrieving ConnectID token"));
            throw new TokenDeniedException();
        }

        throw new TokenUnavailableException();
    }

    public static void linkHqWorker(Context context, String hqUsername, ConnectLinkedAppRecord appRecord, String connectToken) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("token", connectToken);

        String url = ServerUrls.getKeyServer().replace("phone/keys/",
                "settings/users/commcare/link_connectid_user/");

        try {
            ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                    API_VERSION_NONE, new AuthInfo.ProvidedAuth(hqUsername, appRecord.getPassword()), params, true, false);
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Link Connect ID result " + postResult.responseCode);
            if (postResult.e == null && postResult.responseCode == 200) {
                postResult.responseStream.close();

                //Remember that we linked the user successfully
                appRecord.setWorkerLinked(true);
                ConnectAppDatabaseUtil.storeApp(context, appRecord);
            }
        } catch (IOException e) {
            Logger.exception("Linking HQ worker", e);
        }
    }

    public static AuthInfo.TokenAuth retrieveHqTokenSync(Context context, String hqUsername, String connectToken)
            throws TokenUnavailableException {
        HashMap<String, Object> params = new HashMap<>();
        params.put("client_id", "4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV");
        params.put("scope", "mobile_access sync");
        params.put("grant_type", "password");
        params.put("username", hqUsername + "@" + HiddenPreferences.getUserDomain());
        params.put("password", connectToken);

        String host;
        try {
            host = (new URL(ServerUrls.getKeyServer())).getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        String url = "https://" + host + "/oauth/token/";

        ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                API_VERSION_NONE, new AuthInfo.NoAuth(), params, true, false);
        Logger.log(LogTypes.TYPE_MAINTENANCE, "OAuth Token Post Result " + postResult.responseCode);
        if (postResult.responseCode >= 200 && postResult.responseCode < 300) {
            try {
                String responseAsString = new String(StreamsUtil.inputStreamToByteArray(
                        postResult.responseStream));
                JSONObject json = new JSONObject(responseAsString);
                String key = ConnectConstants.CONNECT_KEY_TOKEN;
                String token = json.getString(key);
                Date expiration = new Date();
                key = ConnectConstants.CONNECT_KEY_EXPIRES;
                int seconds = json.has(key) ? json.getInt(key) : 0;
                expiration.setTime(expiration.getTime() + ((long)seconds * 1000));

                String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
                SsoToken ssoToken = new SsoToken(token, expiration);
                ConnectDatabaseHelper.storeHqToken(context, seatedAppId, hqUsername, ssoToken);

                return new AuthInfo.TokenAuth(token);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                Logger.exception("Parsing return from HQ token call", e);
            }
        }

        throw new TokenUnavailableException();
    }


    public static void fetchDbPassphrase(Context context, ConnectUserRecord user, IApiCallback callback) {
        String url = PersonalIdApiClient.BASE_URL + context.getString(R.string.ConnectFetchDbKeyURL);
        ConnectNetworkHelper.get(context,
                url,
                API_VERSION_PERSONAL_ID, new AuthInfo.ProvidedAuth(user.getUserId(), user.getPassword(), false),
                ArrayListMultimap.create(), true, callback);
    }

    public static void confirmBackupCode(Context context,
            String backupCode, String token, IApiCallback callback) {

        HashMap<String, String> params = new HashMap<>();
        params.put("recovery_pin", backupCode);

        AuthInfo authInfo = new AuthInfo.TokenAuth(token);
        String tokenAuth = HttpUtils.getCredential(authInfo);

        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.confirmBackupCode(tokenAuth, params);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.confirmBackupCode);
    }

    public static void startConfiguration(Context context, Map<String, String> body, String integrityToken,
            String requestHash, IApiCallback callback) {
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.startConfiguration(integrityToken, requestHash, body);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.startConfiguration);
    }

    public static void validateFirebaseIdToken(String token, Context context, String firebaseIdToken,
            IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("token", firebaseIdToken);
        AuthInfo authInfo = new AuthInfo.TokenAuth(token);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(tokenAuth);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.validateFirebaseIdToken(tokenAuth,params);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.validateFirebaseIdToken);
    }

    public static void addOrVerifyName(Context context, String name, String token, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("name", name);

        AuthInfo authInfo = new AuthInfo.TokenAuth(token);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(tokenAuth);

        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.checkName(tokenAuth, params);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.checkName);
    }

    public static void updateUserProfile(Context context, String username,
            String password, String displayName,
            String secondaryPhone, IApiCallback callback) {
        //Update the phone number with the server
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String token = HttpUtils.getCredential(authInfo);
        HashMap<String, String> params = new HashMap<>();
        if (secondaryPhone != null) {
            params.put("secondary_phone", secondaryPhone);
        }

        if (displayName != null) {
            params.put("name", displayName);
        }
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.updateProfile(token, params);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.updateProfile);
    }

    public static void setPhotoAndCompleteProfile(Context context, String userName,
            String photoAsBase64, String backupCode, String token, IApiCallback callback) {
        Objects.requireNonNull(photoAsBase64);
        Objects.requireNonNull(userName);
        AuthInfo authInfo = new AuthInfo.TokenAuth(token);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(tokenAuth);

        HashMap<String, String> params = new HashMap<>();
        params.put("photo", photoAsBase64);
        params.put("name", userName);
        params.put("recovery_pin", backupCode);

        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.completeProfile(tokenAuth, params);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.completeProfile);
    }

    public static void retrieveCredentials(Context context, String userName, String password,
            IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(userName, password, false);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.retrieveCredentials(tokenAuth);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.CREDENTIALS);
    }

    public static void sendOtp(Context context, String token, IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.TokenAuth(token);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(tokenAuth);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.sendSessionOtp(tokenAuth);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.sendSessionOtp);
    }

    public static void validateOtp(Context context, String token, String otp, IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.TokenAuth(token);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(tokenAuth);

        HashMap<String, String> params = new HashMap<>();
        params.put("otp", otp);

        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.validateSessionOtp(tokenAuth, params);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.validateSessionOtp);
    }



    public static void retrieveMessages(Context context, String username, String password,IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        Multimap<String, String> params = ArrayListMultimap.create();
        ConnectNetworkHelper.get(context,
                context.getString(R.string.ConnectMessageRetrieveMessagesURL),
                API_VERSION_PERSONAL_ID, authInfo, params, true, callback);
    }

    public static boolean updateChannelConsent(Context context, String username, String password,
                                               String channel, boolean consented,
                                               IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        HashMap<String, Object> params = new HashMap<>();
        params.put("channel", channel);
        params.put("consent", consented);

        return ConnectNetworkHelper.post(context,
                context.getString(R.string.ConnectMessageChannelConsentURL),
                API_VERSION_PERSONAL_ID, authInfo, params, false, false, callback);
    }

    private static void handleApiError(Response<?> response) {
        String message = response.message();
        if (response.code() == 400) {
            // Bad request (e.g., validation failed)
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "Bad Request: " + message);
        } else if (response.code() == 401) {
            // Unauthorized (e.g., invalid credentials)
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "Unauthorized: " + message);
        } else if (response.code() == 404) {
            // Not found
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "Not Found: " + message);
        } else if (response.code() >= 500) {
            // Server error
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "Server Error: " + message);
        } else {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "API Error: " + message);
        }
    }

    public static void retrieveChannelEncryptionKey(Context context, @NonNull ConnectUserRecord user, String channelId, String channelUrl, IApiCallback callback) {
        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                HashMap<String, Object> params = new HashMap<>();
                params.put("channel_id", channelId);

                ConnectNetworkHelper.post(context,
                        channelUrl,
                        null, token, params, true, true, callback);
            }

            @Override
            public void tokenUnavailable() {
                callback.processTokenUnavailableError();
            }

            @Override
            public void tokenRequestDenied() {
                callback.processTokenRequestDeniedError();
            }
        });
    }

    public static void handleReceivedChannelEncryptionKey(Context context, InputStream stream, ConnectMessagingChannelRecord channel) {
        try {
            String responseAsString = new String(
                    StreamsUtil.inputStreamToByteArray(stream));

            if(responseAsString.length() > 0) {
                JSONObject json = new JSONObject(responseAsString);
                channel.setKey(json.getString("key"));
                ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel);
            }
        } catch(JSONException e) {
            throw new RuntimeException(e);
        } catch(IOException e) {
            Logger.exception("Parsing return from key request", e);
        }
    }

    public static void confirmReceivedMessages(Context context, String username, String password,
                                                  List<String> messageIds, IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        HashMap<String, Object> params = new HashMap<>();
        params.put("messages", messageIds);

        ConnectNetworkHelper.post(context,
                context.getString(R.string.ConnectMessageConfirmURL),
                API_VERSION_PERSONAL_ID, authInfo, params, false, true, callback);
    }

    public static void sendMessagingMessage(Context context, String username, String password,
                                            ConnectMessagingMessageRecord message, String key, IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        String[] parts = ConnectMessagingMessageRecord.encrypt(message.getMessage(), key);

        HashMap<String, Object> params = new HashMap<>();
        params.put("channel", message.getChannelId());

        HashMap<String, String> content = new HashMap<>();
        try {
            content.put("ciphertext", parts[0]);
            content.put("nonce", parts[1]);
            content.put("tag", parts[2]);
        } catch(Exception e) {
            Logger.exception("Sending message", e);
        }
        params.put("content", content);
        params.put("timestamp", DateUtils.formatDateTime(message.getTimeStamp(), DateUtils.FORMAT_ISO8601));
        params.put("message_id", message.getMessageId());

        ConnectNetworkHelper.post(context,
                context.getString(R.string.ConnectMessageSendURL),
                API_VERSION_PERSONAL_ID, authInfo, params, false, true, callback);
    }


}
