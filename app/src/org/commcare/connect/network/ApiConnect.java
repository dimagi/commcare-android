package org.commcare.connect.network;

import android.content.Context;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;

import java.util.HashMap;
import java.util.Locale;

public class ApiConnect {
    private static final String API_VERSION_CONNECT = "1.0";

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
