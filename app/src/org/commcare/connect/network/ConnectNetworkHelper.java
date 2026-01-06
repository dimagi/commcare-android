package org.commcare.connect.network;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.dalvik.R;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.CrashUtil;
import org.commcare.utils.GlobalErrors;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import kotlin.Pair;
import kotlin.jvm.Volatile;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * Helper class for making network calls related to Connect
 * Calls may go to ConnectID server or HQ server (for SSO)
 *
 * @author dviggiano
 */
public class ConnectNetworkHelper {
    /**
     * Helper class to hold the results of a network request
     */
    public static class PostResult {
        public final int responseCode;
        public final InputStream responseStream;
        public final Exception e;

        public PostResult(int responseCode, InputStream responseStream, Exception e) {
            this.responseCode = responseCode;
            this.responseStream = responseStream;
            this.e = e;
        }
    }

    @Volatile
    private String callInProgress = null;

    private ConnectNetworkHelper() {
        //Private constructor for singleton
    }

    private static class Loader {
        static final ConnectNetworkHelper INSTANCE = new ConnectNetworkHelper();
    }

    private static ConnectNetworkHelper getInstance() {
        return Loader.INSTANCE;
    }

    private static void setCallInProgress(String call) {
        getInstance().callInProgress = call;
    }


    public static void addVersionHeader(HashMap<String, String> headers, String version) {
        if (version != null) {
            headers.put("Accept", "application/json;version=" + version);
        }
    }

    // this will be removed whenever app has minSDK to 24
    public static PostResult postSync(Context context, String url, String version, AuthInfo authInfo,
                                      HashMap<String, Object> params, boolean useFormEncoding,
                                      boolean background) {
        ConnectNetworkHelper instance = getInstance();

        if (!background) {
            setCallInProgress(url);
            instance.showProgressDialog(context);
        }

        try {
            HashMap<String, String> headers = new HashMap<>();
            RequestBody requestBody = buildPostFormHeaders(params, useFormEncoding, version, headers);

            ModernHttpRequester requester = CommCareApplication.instance().buildHttpRequester(
                    context,
                    url,
                    ImmutableMultimap.of(),
                    headers,
                    requestBody,
                    null,
                    HTTPMethod.POST,
                    authInfo,
                    null,
                    false);

            int responseCode = -1;
            InputStream stream = null;
            IOException exception = null;
            try {
                Response<ResponseBody> response = requester.makeRequest();
                responseCode = response.code();
                if (response.isSuccessful()) {
                    stream = requester.getResponseStream(response);
                } else if (response.errorBody() != null) {
                    String error = response.errorBody().string();
                    Logger.log("Netowrk Error", error);
                }
            } catch (IOException e) {
                exception = e;
                Logger.exception("Exception during POST", e);
            }

            instance.onFinishProcessing(context, background);

            return new PostResult(responseCode, stream, exception);
        } catch (Exception e) {
            if (!background) {
                setCallInProgress(null);
            }
            return new PostResult(-1, null, e);
        }
    }

    public static RequestBody buildPostFormHeaders(HashMap<String, Object> params, boolean useFormEncoding, String version, HashMap<String, String> outputHeaders) {
        RequestBody requestBody;

        if (useFormEncoding) {
            Multimap<String, String> multimap = ArrayListMultimap.create();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                multimap.put(entry.getKey(), entry.getValue().toString());
            }

            requestBody = ModernHttpRequester.getPostBody(multimap);
            outputHeaders = getContentHeadersForXFormPost(requestBody);
        } else {
            Gson gson = new Gson();
            String json = gson.toJson(params);
            requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        }

        addVersionHeader(outputHeaders, version);

        return requestBody;
    }

    private static HashMap<String, String> getContentHeadersForXFormPost(RequestBody postBody) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        try {
            headers.put("Content-Length", String.valueOf(postBody.contentLength()));
        } catch (IOException e) {
            //Empty headers if something goes wrong
        }
        return headers;
    }

    // this will be removed whenever app has minSDK to 24
    private void onFinishProcessing(Context context, boolean background) {
        if (!background) {
            setCallInProgress(null);
            dismissProgressDialog(context);
        }
    }

    public static void handleTokenUnavailableException(Context context) {
        Toast.makeText(context, context.getString(R.string.recovery_network_token_unavailable),
                Toast.LENGTH_LONG).show();
    }

    public static void handleTokenDeniedException() {
        ConnectDatabaseHelper.crashDb(GlobalErrors.PERSONALID_LOST_CONFIGURATION_ERROR);
    }

    private static final int NETWORK_ACTIVITY_ID = 7000;

    private void showProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            Handler handler = new Handler(context.getMainLooper());
            handler.post(() -> {
                try {
                    ((CommCareActivity<?>)context).showProgressDialog(NETWORK_ACTIVITY_ID);
                } catch (Exception e) {
                    //Ignore, ok if showing fails
                }
            });
        }
    }

    private void dismissProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            Handler handler = new Handler(context.getMainLooper());
            handler.post(() -> {
                ((CommCareActivity<?>)context).dismissProgressDialogForTask(NETWORK_ACTIVITY_ID);
            });
        }
    }
}
