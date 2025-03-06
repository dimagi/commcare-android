package org.commcare.connect.network;

import android.content.Context;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.R;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;

public class ApiConnectId {
    private static final String API_VERSION_NONE = null;
    private static final String API_VERSION_CONNECT_ID = "1.0";

    public static void linkHqWorker(Context context, String hqUsername, ConnectLinkedAppRecord appRecord, String connectToken) {
        HashMap<String, String> params = new HashMap<>();
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
        HashMap<String, String> params = new HashMap<>();
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
        } else if(postResult.responseCode == 401) {
            Logger.exception("Invalid ConnectID SSO token", new Exception("Invalid ConnectID token when trying to retrieve HQ token"));
            ConnectSsoHelper.discardTokens(context, hqUsername);
        }

        return null;
    }

    public static ConnectNetworkHelper.PostResult makeHeartbeatRequestSync(Context context) {
        String url = context.getString(R.string.ConnectHeartbeatURL);
        HashMap<String, String> params = new HashMap<>();
        String token = FirebaseMessagingUtil.getFCMToken();
        if(token != null) {
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
            HashMap<String, String> params = new HashMap<>();
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

    public static boolean checkPassword(Context context, String phone, String secret,
                                        String password, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("password", password);

        return ConnectNetworkHelper.post(context, context.getString(R.string.ConnectConfirmPasswordURL),
                API_VERSION_CONNECT_ID, new AuthInfo.NoAuth(), params, false, false, callback);
    }

    public static boolean changePassword(Context context, String username, String oldPassword,
                                         String newPassword, IApiCallback callback) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, oldPassword, false);
        int urlId = R.string.ConnectChangePasswordURL;

        HashMap<String, String> params = new HashMap<>();
        params.put("password", newPassword);

        return ConnectNetworkHelper.post(context, context.getString(urlId), API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean resetPassword(Context context, String phoneNumber, String recoverySecret,
                                        String newPassword, IApiCallback callback) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        AuthInfo authInfo = new AuthInfo.NoAuth();
        int urlId = R.string.ConnectResetPasswordURL;

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phoneNumber);
        params.put("secret_key", recoverySecret);
        params.put("password", newPassword);

        return ConnectNetworkHelper.post(context, context.getString(urlId), API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean checkPin(Context context, String phone, String secret,
                                   String pin, IApiCallback callback) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        AuthInfo authInfo = new AuthInfo.NoAuth();
        int urlId = R.string.ConnectConfirmPinURL;

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("recovery_pin", pin);

        return ConnectNetworkHelper.post(context, context.getString(urlId), API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean changePin(Context context, String username, String password,
                                    String pin, IApiCallback callback) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);
        int urlId = R.string.ConnectSetPinURL;

        HashMap<String, String> params = new HashMap<>();
        params.put("recovery_pin", pin);

        return ConnectNetworkHelper.post(context, context.getString(urlId), API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean checkPhoneAvailable(Context context, String phone, IApiCallback callback) {
        Multimap<String, String> params = ArrayListMultimap.create();
        params.put("phone_number", phone);

        return ConnectNetworkHelper.get(context,
                context.getString(R.string.ConnectPhoneAvailableURL),
                API_VERSION_CONNECT_ID, new AuthInfo.NoAuth(), params, false, callback);
    }

    public static boolean registerUser(Context context, String username, String password, String displayName,
                                       String phone, IApiCallback callback) {
        HashMap<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("password", password);
        params.put("name", displayName);
        params.put("phone_number", phone);
        params.put("fcm_token", FirebaseMessagingUtil.getFCMToken());

        return ConnectNetworkHelper.post(context,
                context.getString(R.string.ConnectRegisterURL),
                API_VERSION_CONNECT_ID, new AuthInfo.NoAuth(), params, false, false, callback);
    }

    public static boolean changePhone(Context context, String username, String password,
                                      String oldPhone, String newPhone, IApiCallback callback) {
        //Update the phone number with the server
        int urlId = R.string.ConnectChangePhoneURL;

        HashMap<String, String> params = new HashMap<>();
        params.put("old_phone_number", oldPhone);
        params.put("new_phone_number", newPhone);

        return ConnectNetworkHelper.post(context, context.getString(urlId), API_VERSION_CONNECT_ID,
                new AuthInfo.ProvidedAuth(username, password, false), params, false, false,
                callback);
    }

    public static boolean updateUserProfile(Context context, String username,
                                            String password, String displayName,
                                            String secondaryPhone, IApiCallback callback) {
        //Update the phone number with the server
        int urlId = R.string.ConnectUpdateProfileURL;

        HashMap<String, String> params = new HashMap<>();
        if(secondaryPhone != null) {
            params.put("secondary_phone", secondaryPhone);
        }

        if(displayName != null) {
            params.put("name", displayName);
        }

        return ConnectNetworkHelper.post(context, context.getString(urlId), API_VERSION_CONNECT_ID,
                new AuthInfo.ProvidedAuth(username, password, false), params, false, false,
                callback);
    }

    public static boolean requestRegistrationOtpPrimary(Context context, String username, String password,
                                                        IApiCallback callback) {
        int urlId = R.string.ConnectValidatePhoneURL;
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        HashMap<String, String> params = new HashMap<>();

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean requestRecoveryOtpPrimary(Context context, String phone, IApiCallback callback) {
        int urlId = R.string.ConnectRecoverURL;
        AuthInfo authInfo = new AuthInfo.NoAuth();

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean requestRecoveryOtpSecondary(Context context, String phone, String secret,
                                                      IApiCallback callback) {
        int urlId = R.string.ConnectRecoverSecondaryURL;
        AuthInfo authInfo = new AuthInfo.NoAuth();

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean requestVerificationOtpSecondary(Context context, String username, String password,
                                                          IApiCallback callback) {
        int urlId = R.string.ConnectVerifySecondaryURL;
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        HashMap<String, String> params = new HashMap<>();

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean confirmRegistrationOtpPrimary(Context context, String username, String password,
                                                        String token, IApiCallback callback) {
        int urlId = R.string.ConnectConfirmOTPURL;
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean confirmRecoveryOtpPrimary(Context context, String phone, String secret,
                                                    String token, IApiCallback callback) {
        int urlId = R.string.ConnectRecoverConfirmOTPURL;
        AuthInfo authInfo = new AuthInfo.NoAuth();

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("token", token);

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean confirmRecoveryOtpSecondary(Context context, String phone, String secret,
                                                      String token, IApiCallback callback) {
        int urlId = R.string.ConnectRecoverConfirmSecondaryOTPURL;
        AuthInfo authInfo = new AuthInfo.NoAuth();

        HashMap<String, String> params = new HashMap<>();
        params.put("phone", phone);
        params.put("secret_key", secret);
        params.put("token", token);

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean confirmVerificationOtpSecondary(Context context, String username, String password,
                                                          String token, IApiCallback callback) {
        int urlId = R.string.ConnectVerifyConfirmSecondaryOTPURL;
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(username, password, false);

        HashMap<String, String> params = new HashMap<>();
        params.put("token", token);

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean requestInitiateAccountDeactivation(Context context, String phone,String secretKey, IApiCallback callback) {
        int urlId = R.string.ConnectInitiateUserAccountDeactivationURL;
        AuthInfo authInfo = new AuthInfo.NoAuth();

        HashMap<String, String> params = new HashMap<>();
        params.put("secret_key", secretKey);
        params.put("phone_number", phone);

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }

    public static boolean confirmUserDeactivation(Context context, String phone, String secret,
                                                  String token, IApiCallback callback) {
        int urlId = R.string.ConnectConfirmUserAccountDeactivationURL;
        AuthInfo authInfo = new AuthInfo.NoAuth();

        HashMap<String, String> params = new HashMap<>();
        params.put("phone_number", phone);
        params.put("secret_key", secret);
        params.put("token", token);

        return ConnectNetworkHelper.post(context, context.getString(urlId),
                API_VERSION_CONNECT_ID, authInfo, params, false, false, callback);
    }
}
