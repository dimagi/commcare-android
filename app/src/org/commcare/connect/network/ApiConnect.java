package org.commcare.connect.network;

import android.content.Context;


import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.network.base.BaseApi;
import org.commcare.connect.network.connect.ConnectApiClient;
import org.commcare.core.network.AuthInfo;
import org.commcare.network.HttpUtils;

import java.util.HashMap;

import androidx.annotation.NonNull;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;

public class ApiConnect {
    private static final String API_VERSION_CONNECT = "1.0";

    public static void getConnectOpportunities(Context context, @NonNull ConnectUserRecord user, IApiCallback callback) {

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {

                String tokenAuth = HttpUtils.getCredential(token);
                ApiService apiService = ConnectApiClient.Companion.getClientApi();
                HashMap<String, String> headers = new HashMap<>();
                ConnectNetworkHelper.addVersionHeader(headers, API_VERSION_CONNECT);
                Call<ResponseBody> call = apiService.getConnectOpportunities(tokenAuth,headers);
                BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.connectOpportunitiesURL);
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

    public static void startLearnApp(Context context, @NonNull ConnectUserRecord user, int jobId, IApiCallback callback) {

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                HashMap<String, Object> params = new HashMap<>();
                params.put("opportunity", String.valueOf(jobId));

                HashMap<String, String> headers = new HashMap<>();
                RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, true, API_VERSION_CONNECT, headers);

                String tokenAuth = HttpUtils.getCredential(token);
                ApiService apiService = ConnectApiClient.Companion.getClientApi();
                Call<ResponseBody> call = apiService.connectStartLearningApp(tokenAuth,headers,requestBody);
                BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.connectStartLearningURL);
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

    public static void getLearningAppProgress(Context context, @NonNull ConnectUserRecord user, int jobId, IApiCallback callback) {

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {

                String tokenAuth = HttpUtils.getCredential(token);
                ApiService apiService = ConnectApiClient.Companion.getClientApi();
                HashMap<String, String> headers = new HashMap<>();
                ConnectNetworkHelper.addVersionHeader(headers, API_VERSION_CONNECT);
                Call<ResponseBody> call = apiService.getConnectLearningAppProgress(tokenAuth,jobId,headers);
                BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.connectLearnProgressURL);
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

    public static void claimJob(Context context, @NonNull ConnectUserRecord user, int jobId, IApiCallback callback) {
        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {

                HashMap<String, Object> params = new HashMap<>();
                HashMap<String, String> headers = new HashMap<>();
                RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, false, API_VERSION_CONNECT, headers);

                String tokenAuth = HttpUtils.getCredential(token);
                ApiService apiService = ConnectApiClient.Companion.getClientApi();
                Call<ResponseBody> call = apiService.connectClaimJob(tokenAuth,jobId,headers,requestBody);
                BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.connectClaimJobURL);

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

    public static void getDeliveries(Context context, @NonNull ConnectUserRecord user, int jobId, IApiCallback callback) {

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                String tokenAuth = HttpUtils.getCredential(token);
                ApiService apiService = ConnectApiClient.Companion.getClientApi();
                HashMap<String, String> headers = new HashMap<>();
                ConnectNetworkHelper.addVersionHeader(headers, API_VERSION_CONNECT);
                Call<ResponseBody> call = apiService.getConnectDeliveries(tokenAuth,jobId,headers);
                BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.connectDeliveriesURL);
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

    public static void setPaymentConfirmed(Context context, @NonNull ConnectUserRecord user, String paymentId, boolean confirmed, IApiCallback callback) {

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                HashMap<String, Object> params = new HashMap<>();
                params.put("confirmed", confirmed ? "true" : "false");

                HashMap<String, String> headers = new HashMap<>();
                RequestBody requestBody = ConnectNetworkHelper.buildPostFormHeaders(params, true, API_VERSION_CONNECT, headers);

                String tokenAuth = HttpUtils.getCredential(token);
                ApiService apiService = ConnectApiClient.Companion.getClientApi();
                Call<ResponseBody> call = apiService.connectPaymentConfirmation(tokenAuth,paymentId,headers,requestBody);
                BaseApi.Companion.callApi(context, call, callback,ApiEndPoints.connectPaymentConfirmationURL);
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
}
