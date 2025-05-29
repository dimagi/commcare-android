package org.commcare.connect.network;

import android.content.Context;
import android.os.Handler;

import com.google.common.collect.ArrayListMultimap;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connectId.ApiClient;
import org.commcare.connect.network.connectId.ApiService;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
import org.commcare.network.HttpUtils;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.commcare.util.LogTypes;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.FirebaseMessagingUtil;
import org.javarosa.core.io.StreamsUtil;
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

import androidx.annotation.NonNull;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;


public class ApiPersonalId {
    private static final String API_VERSION_NONE = null;
    public static final String API_VERSION_PERSONAL_ID = "2.0";
    private static final String HQ_CLIENT_ID = "4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV";
    private static final String CONNECT_CLIENT_ID = "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa";

    public static void linkHqWorker(Context context, String hqUsername, ConnectLinkedAppRecord appRecord, String connectToken) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("token", connectToken);

        String host;
        String domain;
        String url;
        try {
            host = (new URL(ServerUrls.getKeyServer())).getHost();
            domain = HiddenPreferences.getUserDomainWithoutServerUrl();
            String myStr = "https://%s/a/%s/settings/users/commcare/link_connectid_user/";
            url = String.format(myStr, host, domain);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        try {
            ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                    API_VERSION_NONE, new AuthInfo.ProvidedAuth(hqUsername, appRecord.getPassword()), params, true, false);
            if (postResult.responseCode == 200) {
                postResult.responseStream.close();
                appRecord.setWorkerLinked(true);
                ConnectAppDatabaseUtil.storeApp(context, appRecord);
            } else {
                Logger.log("API Error", "API call to link HQ worker failed with code " + postResult.responseCode);
            }
        } catch (IOException e) {
            CrashUtil.log("Linking HQ worker fails");
            CrashUtil.reportException(e);
        }
    }

    public static ConnectNetworkHelper.PostResult makeHeartbeatRequestSync(Context context, AuthInfo.TokenAuth auth) {
        String url = ApiClient.BASE_URL + context.getString(R.string.ConnectHeartbeatURL);
        HashMap<String, Object> params = new HashMap<>();
        String token = FirebaseMessagingUtil.getFCMToken();
        if (token != null) {
            params.put("fcm_token", token);
            boolean useFormEncoding = true;
            return ConnectNetworkHelper.postSync(context, url, API_VERSION_PERSONAL_ID, auth, params, useFormEncoding, true);
        }

        return new ConnectNetworkHelper.PostResult(-1, null, null);
    }

    public static AuthInfo.TokenAuth retrieveConnectIdTokenSync(Context context, @NonNull ConnectUserRecord user) throws
            TokenDeniedException, TokenUnavailableException {
        HashMap<String, Object> params = new HashMap<>();
        params.put("client_id", "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa");
        params.put("scope", "openid");
        params.put("grant_type", "password");
        params.put("username", user.getUserId());
        params.put("password", user.getPassword());

        String url = ApiClient.BASE_URL + context.getString(R.string.ConnectTokenURL);

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
                expiration.setTime(expiration.getTime() + ((long)seconds * 1000));
                user.updateConnectToken(token, expiration);
                ConnectUserDatabaseUtil.storeUser(context, user);

                return new AuthInfo.TokenAuth(token);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                Logger.exception("Parsing return from ConnectID token call", e);
            }
        } else if (postResult.responseCode == 400) {
            throw new TokenDeniedException();
        }

        throw new TokenUnavailableException();
    }

    public static AuthInfo.TokenAuth retrieveHqTokenSync(Context context, String hqUsername, String connectToken) throws TokenUnavailableException {
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
        String url = ApiClient.BASE_URL + context.getString(R.string.ConnectFetchDbKeyURL);
        ConnectNetworkHelper.get(context,
                url,
                API_VERSION_PERSONAL_ID, new AuthInfo.ProvidedAuth(user.getUserId(), user.getPassword(), false),
                ArrayListMultimap.create(), true, callback);
    }

    public static void showProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            Handler handler = new Handler(context.getMainLooper());
            handler.post(() -> {
                try {
                    ((CommCareActivity<?>)context).showProgressDialog(ConnectConstants.NETWORK_ACTIVITY_ID);
                } catch (Exception e) {
                    //Ignore, ok if showing fails
                }
            });
        }
    }

    public static void dismissProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            Handler handler = new Handler(context.getMainLooper());
            handler.post(() -> {
                ((CommCareActivity<?>)context).dismissProgressDialogForTask(ConnectConstants.NETWORK_ACTIVITY_ID);
            });
        }
    }

    private static void callApi(Context context, Call<ResponseBody> call, IApiCallback callback) {
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
                        callback.processFailure(response.code());
                    }
                } else {
                    // Handle validation errors
                    logNetworkError(response);
                    callback.processFailure(response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                dismissProgressDialog(context);
                // Handle network errors, etc.
                handleNetworkError(t);
                callback.processNetworkFailure();
            }
        });
    }

    public static void resetPassword(Context context, String phoneNumber, String recoverySecret,
                                     String newPassword, IApiCallback callback) {

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phoneNumber);
        params.put("secret_key", recoverySecret);
        params.put("password", newPassword);
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.resetPassword(params);
        callApi(context, call, callback);
    }

    public static void confirmBackupCode(Context context,
                                String backupCode,String token ,IApiCallback callback) {

        HashMap<String, String> params = new HashMap<>();
        params.put("recovery_pin", backupCode);

        AuthInfo authInfo = new AuthInfo.TokenAuth(token);
        String tokenAuth = HttpUtils.getCredential(authInfo);

        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.confirmPin(tokenAuth, params);
        callApi(context, call, callback);
    }

    public static void setBackupCode(Context context, String username, String password,
                                     String backupCode, IApiCallback callback) {

        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String token = HttpUtils.getCredential(authInfo);

        HashMap<String, String> params = new HashMap<>();
        params.put("recovery_pin", backupCode);

        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.setBackupCode(token, params);
        callApi(context, call, callback);
    }

    public static void startConfiguration(Context context, Map<String, String> body, String integrityToken,
            String requestHash, IApiCallback callback) {
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.startConfiguration(integrityToken, requestHash, body);
        callApi(context, call, callback);
    }

    public static void validateFirebaseIdToken(String token,Context context, String firebaseIdToken, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("token", firebaseIdToken);
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.validateFirebaseIdToken(token,params);
        callApi(context, call, callback);
    }

    public static void addOrVerifyName(Context context, String name, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("name", name);
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.checkName(params);
        callApi(context, call, callback);
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
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.updateProfile(token, params);
        callApi(context, call, callback);
    }

    public static void setPhotoAndCompleteProfile(Context context, String userId, String password, String userName,
                                                  String photoAsBase64, String backupCode, IApiCallback callback) {
        Objects.requireNonNull(photoAsBase64);
        Objects.requireNonNull(userName);
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(userId, password, false);
        String token = HttpUtils.getCredential(authInfo);
        Objects.requireNonNull(token);

        HashMap<String, String> params = new HashMap<>();
        params.put("photo", photoAsBase64);
        params.put("name", userName);
        params.put("recovery_pin", backupCode);

        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.completeProfile(token, params);
        callApi(context, call, callback);
    }

    public static void requestRegistrationOtpPrimary(Context context, String username, String password,
                                                     IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String token = HttpUtils.getCredential(authInfo);
        HashMap<String, String> params = new HashMap<>();
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.validatePhone(token, params);
        callApi(context, call, callback);
    }

    public static void requestRecoveryOtpPrimary(Context context, String phone, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.requestOTPPrimary(params);
        callApi(context, call, callback);
    }

    public static void confirmRegistrationOtpPrimary(Context context, String username, String password,
                                                     String token, IApiCallback callback) {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        String basicToken = HttpUtils.getCredential(authInfo);
        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);

        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.confirmOTP(basicToken, params);
        callApi(context, call, callback);
    }

    public static void confirmRecoveryOtpPrimary(Context context, String phone, String secret,
                                                 String token, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("token", token);
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.recoverConfirmOTP(params);
        callApi(context, call, callback);
    }

    public static void requestInitiateAccountDeactivation(Context context, String phone, String secretKey, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("secret_key", secretKey);
        params.put("phone_number", phone);
        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.accountDeactivation(params);
        callApi(context, call, callback);
    }

    public static void confirmUserDeactivation(Context context, String phone, String secret,
                                               String token, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone_number", phone);
        params.put("secret_key", secret);
        params.put("token", token);

        ApiService apiService = ApiClient.getClientApi();
        Call<ResponseBody> call = apiService.confirmDeactivation(params);
        callApi(context, call, callback);
    }

    private static void logNetworkError(Response<?> response) {
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

    private static void handleNetworkError(Throwable t) {
        String message = t.getMessage();
        if (t instanceof IOException) {
            // IOException is usually a network error (no internet, timeout, etc.)
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "Network Error: " + message);
        } else if (t instanceof HttpException) {
            // Handle HTTP exceptions separately if needed
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "HTTP Error: " + message);
        } else {
            Logger.log(LogTypes.TYPE_ERROR_SERVER_COMMS, "Unexpected Error: " + message);
        }
    }
}
