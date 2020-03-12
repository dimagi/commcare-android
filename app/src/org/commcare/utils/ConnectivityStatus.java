package org.commcare.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;

import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
import org.commcare.util.LogTypes;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.util.HashMap;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ConnectivityStatus {
    /**
     * Gives fine-grained network connection state.
     * Most should use {@link org.commcare.utils.ConnectivityStatus#isNetworkAvailable(Context)} instead.
     */
    public enum NetworkState {
        /** Network is available. */
        CONNECTED,
        /** Network is not available. */
        DISCONNECTED,
        /** Network is a captive portal. */
        CAPTIVE_PORTAL,
        /** Commcare servers are down. */
        COMMCARE_DOWN
    }

    public static boolean isAirplaneModeOn(Context context) {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return Settings.Global.getInt(context.getApplicationContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } else {
            return Settings.System.getInt(context.getApplicationContext().getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager
                = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
    }

    public static NetworkState checkCaptivePortal() throws IOException {
        // CommCare has its own URL for detecting Captive Portals.
        // It must return an HTTP status code of 200 and a body containing "success".
        String captivePortalURL = "http://www.commcarehq.org/serverup.txt";
        CommCareNetworkService commCareNetworkService = CommCareNetworkServiceGenerator.createNoAuthCommCareNetworkService();
        Response<ResponseBody> response = commCareNetworkService.makeGetRequest(captivePortalURL, new HashMap<>(), new HashMap<>()).execute();
        Logger.log(LogTypes.TYPE_USER, "CCHQ ping test. Response Code: " + response.code() + " and Response Body: " + response.body());

        if (response.code() != 200) {
            return NetworkState.COMMCARE_DOWN;
        } else if (!"success".equals(response.body().string())) {
            return NetworkState.CAPTIVE_PORTAL;
        } else {
            return NetworkState.CONNECTED;
        }
    }
}
