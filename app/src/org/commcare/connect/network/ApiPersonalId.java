package org.commcare.connect.network;

import android.content.Context;

import androidx.annotation.NonNull;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;

import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.network.base.BaseApi;
import org.commcare.connect.network.connectId.PersonalIdApiClient;
import org.commcare.core.network.AuthInfo;
import org.commcare.network.HttpUtils;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.utils.FirebaseMessagingUtil;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;


public class ApiPersonalId {
    private static final String CONNECT_CLIENT_ID = "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa";


    public static void makeHeartbeatRequest(Context context, @NonNull ConnectUserRecord user, IApiCallback callback) {
        ConnectSsoHelper.retrievePersonalIdToken(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {

                HashMap<String, Object> params = new HashMap<>();
                String firebaseToken = FirebaseMessagingUtil.getFCMToken();
                if (firebaseToken != null) {
                    params.put("fcm_token", firebaseToken);
                }

                String tokenAuth = HttpUtils.getCredential(token);
                HashMap<String, String> headers = new HashMap<>();
                RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, true, PersonalIdApiClient.API_VERSION, headers);
                ApiService apiService = PersonalIdApiClient.getClientApi();
                Call<ResponseBody> call = apiService.connectHeartbeat(tokenAuth,headers, requestBody);
                BaseApi.Companion.callApi(context, call, callback, ApiEndPoints.connectHeartbeatURL);
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

    public static void retrievePersonalIdToken(Context context, @NonNull ConnectUserRecord user, IApiCallback callback) {

        HashMap<String, Object> params = new HashMap<>();
        params.put("client_id", CONNECT_CLIENT_ID);
        params.put("scope", "openid");
        params.put("grant_type", "password");
        params.put("username", user.getUserId());
        params.put("password", user.getPassword());

        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, true, PersonalIdApiClient.API_VERSION, headers);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.connectToken(headers,requestBody);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.connectTokenURL);
    }

    public static void linkHqWorker(Context context, String hqUsername, ConnectLinkedAppRecord appRecord, String connectToken, IApiCallback callback) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("token", connectToken);

        String url = ServerUrls.getKeyServer().replace("phone/keys/",
                "settings/users/commcare/link_connectid_user/");


        AuthInfo authInfo = new AuthInfo.ProvidedAuth(hqUsername, appRecord.getPassword());
        String tokenAuth = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(tokenAuth);

        HashMap<String, String> headers = new HashMap<>();
        makePostRequestWithUrl(context, url, tokenAuth, params, headers, true, callback);
    }

    public static void retrieveHqToken(Context context, String hqUsername, String connectToken, IApiCallback callback) {
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

        HashMap<String, String> headers = new HashMap<>();
        makePostRequestWithUrl(context, url, null, params, headers, true, callback);
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

    public static void reportIntegrity(Context context, Map<String, String> body, String integrityToken,
                                          String requestHash, IApiCallback callback) {
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.reportIntegrity(integrityToken, requestHash, body);
        BaseApi.Companion.callApi(context, call, callback, ApiEndPoints.reportIntegrity);
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

    public static void retrieveWorkHistory(Context context, String userId, String password,
            IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(userId, password, false);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.retrieveCredentials(tokenAuth);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.CREDENTIALS);
    }

    public static void retrieveNotifications(Context context, String userId, String password,
                                             IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(userId, password, false);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.getAllNotifications(tokenAuth);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.RETRIEVE_NOTIFICATIONS);
    }

    public static void updateNotifications(Context context, String userId, String password, IApiCallback callback, List<String> notificationId) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(userId, password, false);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        HashMap<String, Object> params = new HashMap<>();
        params.put("notifications", notificationId);
        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, false, PersonalIdApiClient.API_VERSION, headers);
        Call<ResponseBody> call = apiService.updateNotification(tokenAuth, headers, requestBody);
        BaseApi.Companion.callApi(context, call, callback, ApiEndPoints.UPDATE_NOTIFICATIONS);
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

    public static void updateChannelConsent(Context context, String username, String password,
                                               String channel, boolean consented,
                                               IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(tokenAuth);

        HashMap<String, Object> params = new HashMap<>();
        params.put("channel", channel);
        params.put("consent", consented);
        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, false, PersonalIdApiClient.API_VERSION, headers);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.updateChannelConsent(tokenAuth, headers,requestBody);
        BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.CONNECT_MESSAGE_CHANNEL_CONSENT_URL);
    }

    public static void retrieveChannelEncryptionKey(Context context, @NonNull ConnectUserRecord user, String channelId, String channelUrl, IApiCallback callback) {
        ConnectSsoHelper.retrievePersonalIdToken(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth tokenAuth) {
                HashMap<String, Object> params = new HashMap<>();
                params.put("channel_id", channelId);
                HashMap<String, String> headers = new HashMap<>();
                String token = HttpUtils.getCredential(tokenAuth);
                makePostRequestWithUrl(context, channelUrl, token, params, headers, true, callback);
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

    private static void makePostRequestWithUrl(Context context,
                                               String channelUrl,
                                               String token,
                                               HashMap<String, Object> params,
                                               HashMap<String, String> headers,
                                               boolean useFormEncoding,
                                               IApiCallback callback){
        RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, useFormEncoding, PersonalIdApiClient.API_VERSION, headers);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.makePostRequest(channelUrl, token, headers, requestBody);
        BaseApi.Companion.callApi(context, call, callback,channelUrl);
    }

    public static void sendMessagingMessage(Context context, String username, String password,
                                            ConnectMessagingMessageRecord message, String key, IApiCallback callback) {

        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String tokenAuth = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(tokenAuth);

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


        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, false, PersonalIdApiClient.API_VERSION, headers);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.sendMessagingMessage(tokenAuth, headers,requestBody);
        BaseApi.Companion.callApi(context, call, callback, ApiEndPoints.CONNECT_MESSAGE_SEND_URL);
    }

    public static void getReleaseToggles(
            Context context,
            String userId,
            String password,
            IApiCallback callback
    ) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(userId, password, false);
        String authToken = HttpUtils.getCredential(authInfo);
        ApiService apiService = PersonalIdApiClient.getClientApi();
        Call<ResponseBody> call = apiService.getReleaseToggles(authToken);
        BaseApi.Companion.callApi(context, call, callback, ApiEndPoints.RELEASE_TOGGLES);
    }
}
