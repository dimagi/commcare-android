package org.commcare.connect.network;

import android.content.Context;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.network.connectId.ApiEndPoints;
import org.commcare.core.network.AuthInfo;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;

import java.util.HashMap;
import java.util.Locale;

import androidx.annotation.NonNull;

public class ApiConnect {
    private static final String API_VERSION_CONNECT = "1.0";

    public static boolean getConnectOpportunities(Context context, @NonNull ConnectUserRecord user, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                String url = String.format(ApiEndPoints.connectOpportunitiesURL, BuildConfig.CCC_HOST);
                Multimap<String, String> params = ArrayListMultimap.create();

                ConnectNetworkHelper.get(context, url, API_VERSION_CONNECT, token, params, false, handler);
            }

            @Override
            public void tokenUnavailable() {
                handler.processTokenUnavailableError();
            }

            @Override
            public void tokenRequestDenied() {
                handler.processTokenRequestDeniedError();
            }
        });

        return true;
    }

    public static boolean startLearnApp(Context context, @NonNull ConnectUserRecord user, int jobId, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                String url = String.format(ApiEndPoints.connectStartLearningURL, BuildConfig.CCC_HOST);
                HashMap<String, Object> params = new HashMap<>();
                params.put("opportunity", String.format(Locale.getDefault(), "%d", jobId));

                ConnectNetworkHelper.post(context, url, API_VERSION_CONNECT, token, params, true, false, handler);
            }

            @Override
            public void tokenUnavailable() {
                handler.processTokenUnavailableError();
            }

            @Override
            public void tokenRequestDenied() {
                handler.processTokenRequestDeniedError();
            }
        });

        return true;
    }

    public static boolean getLearnProgress(Context context, @NonNull ConnectUserRecord user, int jobId, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                String url = String.format(ApiEndPoints.connectLearnProgressURL, BuildConfig.CCC_HOST, jobId);
                Multimap<String, String> params = ArrayListMultimap.create();

                ConnectNetworkHelper.get(context, url, API_VERSION_CONNECT, token, params, false, handler);
            }

            @Override
            public void tokenUnavailable() {
                handler.processTokenUnavailableError();
            }

            @Override
            public void tokenRequestDenied() {
                handler.processTokenRequestDeniedError();
            }
        });

        return true;
    }

    public static boolean claimJob(Context context, @NonNull ConnectUserRecord user, int jobId, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                String url = String.format(ApiEndPoints.connectClaimJobURL, BuildConfig.CCC_HOST, jobId);
                HashMap<String, Object> params = new HashMap<>();

                ConnectNetworkHelper.post(context, url, API_VERSION_CONNECT, token, params, false, false, handler);
            }

            @Override
            public void tokenUnavailable() {
                handler.processTokenUnavailableError();
            }

            @Override
            public void tokenRequestDenied() {
                handler.processTokenRequestDeniedError();
            }
        });

        return true;
    }

    public static boolean getDeliveries(Context context, @NonNull ConnectUserRecord user, int jobId, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                String url = String.format(ApiEndPoints.connectDeliveriesURL, BuildConfig.CCC_HOST, jobId);
                Multimap<String, String> params = ArrayListMultimap.create();

                ConnectNetworkHelper.get(context, url, API_VERSION_CONNECT, token, params, false, handler);
            }

            @Override
            public void tokenUnavailable() {
                handler.processTokenUnavailableError();
            }

            @Override
            public void tokenRequestDenied() {
                handler.processTokenRequestDeniedError();
            }
        });

        return true;
    }

    public static boolean setPaymentConfirmed(Context context, @NonNull ConnectUserRecord user, String paymentId, boolean confirmed, IApiCallback handler) {
        if (ConnectNetworkHelper.isBusy()) {
            return false;
        }

        ConnectSsoHelper.retrieveConnectIdTokenAsync(context, user, new ConnectSsoHelper.TokenCallback() {
            @Override
            public void tokenRetrieved(AuthInfo.TokenAuth token) {
                String url = String.format(ApiEndPoints.connectPaymentConfirmationURL, BuildConfig.CCC_HOST, paymentId);
                HashMap<String, Object> params = new HashMap<>();
                params.put("confirmed", confirmed ? "true" : "false");

                ConnectNetworkHelper.post(context, url, API_VERSION_CONNECT, token, params, true, false, handler);
            }

            @Override
            public void tokenUnavailable() {
                handler.processTokenUnavailableError();
            }

            @Override
            public void tokenRequestDenied() {
                handler.processTokenRequestDeniedError();
            }
        });

        return true;
    }
}
