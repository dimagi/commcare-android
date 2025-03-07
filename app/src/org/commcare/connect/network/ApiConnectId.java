package org.commcare.connect.network;

import android.content.Context;
import android.os.Handler;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.network.HttpUtils;
import org.commcare.network.connectId.network.ApiClient;
import org.commcare.network.connectId.network.ApiService;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.util.LogTypes;
import org.commcare.utils.CrashUtil;
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
import java.util.List;

import androidx.annotation.NonNull;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;


public class ApiConnectId {
    private static final String API_VERSION_NONE = null;
    public static final String API_VERSION_CONNECT_ID = "1.0";
    private static final int NETWORK_ACTIVITY_ID = 7000;

    private static ApiService apiService;
    public ApiConnectId() {
    }
    public static void linkHqWorker(Context context, String hqUsername, ConnectLinkedAppRecord appRecord, String connectToken) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("token", connectToken);

            String url = ServerUrls.getKeyServer().replace("phone/keys/",
                    "settings/users/commcare/link_connectid_user/");

        try {
            ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                    API_VERSION_NONE, new AuthInfo.ProvidedAuth(hqUsername, appRecord.getPassword()), params, true, false);
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Link Connect ID result " + postResult.responseCode );
            if (postResult.e == null && postResult.responseCode == 200) {
                postResult.responseStream.close();

                    //Remember that we linked the user successfully
                    appRecord.setWorkerLinked(true);
                    ConnectDatabaseHelper.storeApp(context, appRecord);
                }
            } catch (IOException e) {
                Logger.exception("Linking HQ worker", e);
            }
        }


    public static AuthInfo.TokenAuth retrieveHqTokenApi(Context context, String hqUsername, String connectToken) {
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
                    SsoToken ssoToken = new SsoToken(token, expiration);
                    ConnectDatabaseHelper.storeHqToken(context, seatedAppId, hqUsername, ssoToken);

                    return new AuthInfo.TokenAuth(token);
                } else  {
                    Logger.log(LogTypes.TYPE_MAINTENANCE, "Connect access Token not present in oauth response");
                }
            } catch (IOException | JSONException e) {
                Logger.exception("Parsing return from HQ OIDC call", e);
            }
        }

        return null;
    }

    public static ConnectNetworkHelper.PostResult makeHeartbeatRequestSync(Context context) {
        String url = context.getString(R.string.ConnectHeartbeatURL);
        HashMap<String, Object> params = new HashMap<>();
        String token = FirebaseMessagingUtil.getFCMToken();
        if (token != null) {
            params.put("fcm_token", token);
            boolean useFormEncoding = true;
            return ConnectNetworkHelper.postSync(context, url, API_VERSION_CONNECT_ID, retrieveConnectIdTokenSync(context), params, useFormEncoding, true);
        }

        return new ConnectNetworkHelper.PostResult(-1, null, null);
    }

    public static AuthInfo.TokenAuth retrieveConnectIdTokenSync(Context context) {
        AuthInfo.TokenAuth connectToken = ConnectManager.getConnectToken();
        if (connectToken != null) {
            return connectToken;
        }

        ConnectUserRecord user = ConnectDatabaseHelper.getUser(context);

        if (user != null) {
            HashMap<String, Object> params = new HashMap<>();
            params.put("client_id", "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa");
            params.put("scope", "openid");
            params.put("grant_type", "password");
            params.put("username", user.getUserId());
            params.put("password", user.getPassword());

            String url = context.getString(R.string.ConnectTokenURL);

            ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                    API_VERSION_CONNECT_ID, new AuthInfo.NoAuth(), params, true, false);
            Logger.log(LogTypes.TYPE_MAINTENANCE, "Connect Token Post Result " + postResult.responseCode);
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
                    } else {
                        Logger.log(LogTypes.TYPE_MAINTENANCE, "Connect Token Post Result doesn't have token");
                    }
                } catch (IOException | JSONException e) {
                    Logger.exception("Parsing return from Connect OIDC call", e);
                }
            }
        }

        return null;
    }

    public static void fetchDbPassphrase(Context context, ConnectUserRecord user, IApiCallback callback) {
        ConnectNetworkHelper.get(context,
                context.getString(R.string.ConnectFetchDbKeyURL),
                API_VERSION_CONNECT_ID, new AuthInfo.ProvidedAuth(user.getUserId(), user.getPassword(), false),
                ArrayListMultimap.create(), true, callback);
    }

    public static void showProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            Handler handler = new Handler(context.getMainLooper());
            handler.post(() -> {
                try {
                    ((CommCareActivity<?>)context).showProgressDialog(NETWORK_ACTIVITY_ID);
                } catch(Exception e) {
                    //Ignore, ok if showing fails
                }
            });
        }
    }

    public static void dismissProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            Handler handler = new Handler(context.getMainLooper());
            handler.post(() -> {
                ((CommCareActivity<?>)context).dismissProgressDialogForTask(NETWORK_ACTIVITY_ID);
            });
        }
    }
    static void callApi(Context context,Call<ResponseBody> call, IApiCallback callback) {
        showProgressDialog(context);
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                dismissProgressDialog(context);
                if (response.isSuccessful() && response.body() != null) {
                    // Handle success
                    try (InputStream responseStream = response.body().byteStream()) {
                        callback.processSuccess(response.code(), responseStream);
                    } catch (IOException e) {
                        // Handle error when reading the stream
                        callback.processFailure(response.code(), e);
                    }
                } else {
                    // Handle validation errors
                    handleApiError(response);
                    callback.processFailure(response.code(), null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                dismissProgressDialog(context);
                // Handle network errors, etc.
                handleNetworkError(t);
                callback.processNetworkFailure();

            }
        });
    }

    public static void checkPassword(Context context, String phone, String secret,
                                     String password, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("password", password);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.checkPassword(params);
        callApi(context,call,callback);
    }

    public static void resetPassword(Context context, String phoneNumber, String recoverySecret,
                                     String newPassword, IApiCallback callback) {

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phoneNumber);
        params.put("secret_key", recoverySecret);
        params.put("password", newPassword);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.resetPassword(params);
        callApi(context,call, callback);
    }

    public static void checkPin(Context context, String phone, String secret,
                                String pin, IApiCallback callback) {

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("recovery_pin", pin);

        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.confirmPIN(params);
        callApi(context,call,callback);
    }

    public static void changePin(Context context, String username, String password,
                                 String pin, IApiCallback callback) {

        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String token = HttpUtils.getCredential(authInfo);

        HashMap<String, String> params = new HashMap<>();
        params.put("recovery_pin", pin);

        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.changePIN(token, params);
        callApi(context,call,callback);
    }

    public static void checkPhoneAvailable(Context context, String phone, IApiCallback callback) {
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.checkPhoneNumber(phone);
        callApi(context,call,callback);
    }

    public static void registerUser(Context context, String username, String password, String displayName,
                                       String phone, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("password", password);
        params.put("name", displayName);
        params.put("phone_number", phone);
        params.put("fcm_token", FirebaseMessagingUtil.getFCMToken());
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.registerUser(params);
        callApi(context,call,callback);
    }


    public static void changePhone(Context context, String username, String password,
                                   String oldPhone, String newPhone, IApiCallback callback) {
        //Update the phone number with the server
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String token = HttpUtils.getCredential(authInfo);
        HashMap<String, String> params = new HashMap<>();
        params.put("old_phone_number", oldPhone);
        params.put("new_phone_number", newPhone);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.changePhoneNo(token, params);
        callApi(context,call,callback);
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
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.updateProfile(token, params);
        callApi(context,call,callback);
    }

    public static void requestRegistrationOtpPrimary(Context context, String username, String password,
                                                     IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String token = HttpUtils.getCredential(authInfo);
        HashMap<String, String> params = new HashMap<>();
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.validatePhone(token,params);
        callApi(context,call,callback);
    }

    public static void requestRecoveryOtpPrimary(Context context, String phone, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.requestOTPPrimary(params);
        callApi(context,call,callback);
    }

    public static void requestRecoveryOtpSecondary(Context context, String phone, String secret,
                                                   IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.recoverSecondary(params);
        callApi(context,call,callback);
    }

    public static void requestVerificationOtpSecondary(Context context, String username, String password,
                                                       IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String basicToken= HttpUtils.getCredential(authInfo);
        HashMap<String, String> params = new HashMap<>();
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.validateSecondaryPhone(basicToken,params);
        callApi(context,call,callback);
    }

    public static void confirmRegistrationOtpPrimary(Context context, String username, String password,
                                                        String token, IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String basicToken= HttpUtils.getCredential(authInfo);
        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);

        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.confirmOTP(basicToken,params);
        callApi(context,call,callback);
    }

    public static void confirmRecoveryOtpPrimary(Context context, String phone, String secret,
                                                 String token, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("token", token);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.recoverConfirmOTP(params);
        callApi(context,call,callback);
    }

    public static void confirmRecoveryOtpSecondary(Context context, String phone, String secret,
                                                   String token, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("token", token);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.recoverConfirmOTPSecondary(params);
        callApi(context,call,callback);
    }

    public static void confirmVerificationOtpSecondary(Context context, String username, String password,
                                                       String token, IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String token1 = HttpUtils.getCredential(authInfo);
        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.confirmOTPSecondary(token1, params);
        callApi(context,call,callback);
    }

    public static void requestInitiateAccountDeactivation(Context context, String phone, String secretKey, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("secret_key", secretKey);
        params.put("phone_number", phone);
        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.accountDeactivation(params);
        callApi(context,call,callback);
    }

    public static void confirmUserDeactivation(Context context, String phone, String secret,
                                               String token, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone_number", phone);
        params.put("secret_key", secret);
        params.put("token", token);

        apiService = ApiClient.getClient().create(ApiService.class);
        Call<ResponseBody> call = apiService.confirmDeactivation(params);
        callApi(context,call,callback);
    }

    private static void handleApiError(Response<?> response) {
        CrashUtil.reportException(new Exception(response.message()));
        if (response.code() == 400) {
            // Bad request (e.g., validation failed)
            System.out.println("Bad Request: " + response.message());
        } else if (response.code() == 401) {
            // Unauthorized (e.g., invalid credentials)
            System.out.println("Unauthorized: " + response.message());
        } else if (response.code() == 404) {
            // Not found
            System.out.println("Not Found: " + response.message());
        } else if (response.code() >= 500) {
            // Server error
            System.out.println("Server Error: " + response.message());
        } else {
            System.out.println("API Error: " + response.message());
        }
    }

    private static void handleNetworkError(Throwable t) {
        if (t instanceof IOException) {
            // IOException is usually a network error (no internet, timeout, etc.)
            System.out.println("Network Error: " + t.getMessage());
        } else if (t instanceof HttpException) {
            // Handle HTTP exceptions separately if needed
            System.out.println("HTTP Error: " + t.getMessage());
        } else {
            System.out.println("Unexpected Error: " + t.getMessage());
        }
    }
    public static void retrieveMessages(Context context, String username, String password,IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        Multimap<String, String> params = ArrayListMultimap.create();
        ConnectNetworkHelper.get(context,
                context.getString(R.string.ConnectMessageRetrieveMessagesURL),
                API_VERSION_CONNECT_ID, authInfo, params, true, callback);
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
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static void retrieveChannelEncryptionKeySync(Context context, ConnectMessagingChannelRecord channel) {
        AuthInfo.TokenAuth auth = ApiConnectId.retrieveConnectIdTokenSync(context);
        if(auth != null) {
            HashMap<String, Object> params = new HashMap<>();
            params.put("channel_id", channel.getChannelId());

            ConnectNetworkHelper.PostResult result = ConnectNetworkHelper.postSync(context,
                    channel.getKeyUrl(), null, auth, params, true, true);

            if(result.responseCode >= 200 && result.responseCode < 300) {
                handleReceivedEncryptionKey(context, result.responseStream, channel);
            }
        }
    }

    public static void retrieveChannelEncryptionKey(Context context, String channelId, String channelUrl, IApiCallback callback) {
        ConnectSsoHelper.retrieveConnectTokenAsync(context, token -> {
            HashMap<String, Object> params = new HashMap<>();
            params.put("channel_id", channelId);

            ConnectNetworkHelper.post(context,
                    channelUrl,
                    null, token, params, true, true, callback);
        });
    }

    public static void handleReceivedEncryptionKey(Context context, InputStream stream, ConnectMessagingChannelRecord channel) {
        try {
            String responseAsString = new String(
                    StreamsUtil.inputStreamToByteArray(stream));

            if(responseAsString.length() > 0) {
                JSONObject json = new JSONObject(responseAsString);
                channel.setKey(json.getString("key"));
                ConnectDatabaseHelper.storeMessagingChannel(context, channel);
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
                API_VERSION_CONNECT_ID, authInfo, params, false, true, callback);
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
                API_VERSION_CONNECT_ID, authInfo, params, false, true, callback);
    }
}
