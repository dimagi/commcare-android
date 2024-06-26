package org.commcare.connect.network;

import android.content.Context;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.commcare.CommCareApplication;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.commcare.preferences.HiddenPreferences;
import org.commcare.preferences.ServerUrls;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class ApiConnect {
    private static final String API_VERSION_NONE = null;
    private static final String API_VERSION_CONNECT = "1.0";

    public static void linkHqWorker(Context context, String hqUsername, String hqPassword, String connectToken) {
        String seatedAppId = CommCareApplication.instance().getCurrentApp().getUniqueId();
        ConnectLinkedAppRecord appRecord = ConnectDatabaseHelper.getAppData(context, seatedAppId, hqUsername);
        if (appRecord != null && !appRecord.getWorkerLinked()) {
            HashMap<String, String> params = new HashMap<>();
            params.put("token", connectToken);

            String url = ServerUrls.getKeyServer().replace("phone/keys/",
                    "settings/users/commcare/link_connectid_user/");

            try {
                ConnectNetworkHelper.PostResult postResult = ConnectNetworkHelper.postSync(context, url,
                        API_VERSION_NONE, new AuthInfo.ProvidedAuth(hqUsername, hqPassword), params, true, false);
                if (postResult.e == null && postResult.responseCode == 200) {
                    postResult.responseStream.close();

                    //Remember that we linked the user successfully
                    appRecord.setWorkerLinked(true);
                    ConnectDatabaseHelper.storeApp(context, appRecord);
                }
            } catch (IOException e) {
                //Don't care for now
            }
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
                    ConnectDatabaseHelper.storeHqToken(context, seatedAppId, hqUsername, token, expiration);

                    return new AuthInfo.TokenAuth(token);
                }
            } catch (IOException | JSONException e) {
                Logger.exception("Parsing return from HQ OIDC call", e);
            }
        }

        return null;
    }

    public static boolean getConnectOpportunities(Context context, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectTokenAsync(context, token -> {
            if(token == null) {
                return;
            }

            String url = context.getString(R.string.ConnectOpportunitiesURL, BuildConfig.CCC_HOST);
            Multimap<String, String> params = ArrayListMultimap.create();

            ConnectNetworkHelper.get(context, url, API_VERSION_CONNECT, token, params, false, handler);
        });

        return true;
    }

    public static boolean startLearnApp(Context context, int jobId, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectTokenAsync(context, token -> {
            if(token == null) {
                return;
            }

            String url = context.getString(R.string.ConnectStartLearningURL, BuildConfig.CCC_HOST);
            HashMap<String, String> params = new HashMap<>();
            params.put("opportunity", String.format(Locale.getDefault(), "%d", jobId));

            ConnectNetworkHelper.post(context, url, API_VERSION_CONNECT, token, params, true, false, handler);
        });

        return true;
    }

    public static boolean getLearnProgress(Context context, int jobId, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectTokenAsync(context, token -> {
            if(token == null) {
                return;
            }

            String url = context.getString(R.string.ConnectLearnProgressURL, BuildConfig.CCC_HOST, jobId);
            Multimap<String, String> params = ArrayListMultimap.create();

            ConnectNetworkHelper.get(context, url, API_VERSION_CONNECT, token, params, false, handler);
        });

        return true;
    }

    public static boolean claimJob(Context context, int jobId, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectTokenAsync(context, token -> {
            if(token == null) {
                return;
            }

            String url = context.getString(R.string.ConnectClaimJobURL, BuildConfig.CCC_HOST, jobId);
            HashMap<String, String> params = new HashMap<>();

            ConnectNetworkHelper.post(context, url, API_VERSION_CONNECT, token, params, false, false, handler);
        });

        return true;
    }

    public static boolean getDeliveries(Context context, int jobId, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectTokenAsync(context, token -> {
            if(token == null) {
                return;
            }

            String url = context.getString(R.string.ConnectDeliveriesURL, BuildConfig.CCC_HOST, jobId);
            Multimap<String, String> params = ArrayListMultimap.create();

            ConnectNetworkHelper.get(context, url, API_VERSION_CONNECT, token, params, false, handler);
        });

        return true;
    }

    public static boolean setPaymentConfirmed(Context context, String paymentId, boolean confirmed, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectTokenAsync(context, token -> {
            if(token == null) {
                return;
            }

            String url = context.getString(R.string.ConnectPaymentConfirmationURL, BuildConfig.CCC_HOST, paymentId);

            HashMap<String, String> params = new HashMap<>();
            params.put("confirmed", confirmed ? "true" : "false");

            ConnectNetworkHelper.post(context, url, API_VERSION_CONNECT, token, params, true, false, handler);
        });

        return true;
    }
}
