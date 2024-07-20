package org.commcare.connect.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.widget.Toast;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.dalvik.R;
import org.commcare.interfaces.ConnectorWithHttpResponseProcessor;
import org.commcare.tasks.ModernHttpTask;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.CrashUtil;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

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
        public final IOException e;

        public PostResult(int responseCode, InputStream responseStream, IOException e) {
            this.responseCode = responseCode;
            this.responseStream = responseStream;
            this.e = e;
        }
    }

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

    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    public static Date parseDate(String dateStr) throws ParseException {
        Date issueDate=dateFormat.parse(dateStr);
        return issueDate;
    }

    private static final SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());

    public static Date convertUTCToDate(String utcDateString) {
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        Date date = null;
        try {
            date = utcFormat.parse(utcDateString);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return date;
    }

    public static Date convertDateToLocal(Date utcDate) {
        utcFormat.setTimeZone(TimeZone.getDefault());

        Date date = null;
        try {
            String localDateString = utcFormat.format(utcDate);
            date = utcFormat.parse(localDateString);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return date;
    }

    public static String getCallInProgress() {
        return getInstance().callInProgress;
    }

    public static boolean isBusy() {
        return getCallInProgress() != null;
    }

    private static void setCallInProgress(String call) {
        getInstance().callInProgress = call;
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager manager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = manager.getActiveNetwork();
            if(network == null) {
                return false;
            }

            NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        } else {
            NetworkInfo info = manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    public static boolean post(Context context, String url, String version, AuthInfo authInfo,
                               HashMap<String, String> params, boolean useFormEncoding,
                               boolean background, IApiCallback handler) {
        return getInstance().postInternal(context, url, version, authInfo, params, useFormEncoding,
                background, handler);
    }

    public static boolean get(Context context, String url, String version, AuthInfo authInfo,
                              Multimap<String, String> params, boolean background, IApiCallback handler) {
        return getInstance().getInternal(context, url, version, authInfo, params, background, handler);
    }

    private static void addVersionHeader(HashMap<String, String> headers, String version) {
        if(version != null) {
            headers.put("Accept", "application/json;version=" + version);
        }
    }

    public static PostResult postSync(Context context, String url, String version, AuthInfo authInfo,
                                      HashMap<String, String> params, boolean useFormEncoding,
                                      boolean background) {
        ConnectNetworkHelper instance = getInstance();

        if(!background) {
            setCallInProgress(url);
            instance.showProgressDialog(context);
        }

        try {
            HashMap<String, String> headers = new HashMap<>();
            RequestBody requestBody;

            if (useFormEncoding) {
                Multimap<String, String> multimap = ArrayListMultimap.create();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    multimap.put(entry.getKey(), entry.getValue());
                }

                requestBody = ModernHttpRequester.getPostBody(multimap);
                headers = getContentHeadersForXFormPost(requestBody);
            } else {
                Gson gson = new Gson();
                String json = gson.toJson(params);
                requestBody = RequestBody.create(MediaType.parse("application/json"), json);
            }

            addVersionHeader(headers, version);

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
            }

            instance.onFinishProcessing(context, background);

            return new PostResult(responseCode, stream, exception);
        }
        catch(Exception e) {
            if(!background) {
                setCallInProgress(null);
            }
            return new PostResult(-1, null, null);
        }
    }

    private boolean postInternal(Context context, String url, String version, AuthInfo authInfo,
                                 HashMap<String, String> params, boolean useFormEncoding,
                                 boolean background, IApiCallback handler) {
        if(!background) {
            if (isBusy()) {
                return false;
            }
            setCallInProgress(url);

            showProgressDialog(context);
        }

        HashMap<String, String> headers = new HashMap<>();
        RequestBody requestBody;

        if (useFormEncoding) {
            Multimap<String, String> multimap = ArrayListMultimap.create();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                multimap.put(entry.getKey(), entry.getValue());
            }

            requestBody = ModernHttpRequester.getPostBody(multimap);
            headers = getContentHeadersForXFormPost(requestBody);
        } else {
            Gson gson = new Gson();
            String json = gson.toJson(params);
            requestBody = RequestBody.create(MediaType.parse("application/json"), json);
        }

        addVersionHeader(headers, version);

        ModernHttpTask postTask =
                new ModernHttpTask(context, url,
                        ImmutableMultimap.of(),
                        headers,
                        requestBody,
                        HTTPMethod.POST,
                        authInfo);
        postTask.connect(getResponseProcessor(context, url, background, handler));

        postTask.executeParallel();

        return true;
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

    public PostResult getSync(Context context, String url, AuthInfo authInfo, boolean background,
                                        Multimap<String, String> params) {
        if(!background) {
            setCallInProgress(url);
            showProgressDialog(context);
        }

        HashMap<String, String> headers = new HashMap<>();

        //TODO: Figure out how to send GET request the right way
        StringBuilder getUrl = new StringBuilder(url);
        if (params.size() > 0) {
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entries()) {
                String delim = "&";
                if (first) {
                    delim = "?";
                    first = false;
                }
                getUrl.append(delim).append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        ModernHttpRequester requester = CommCareApplication.instance().buildHttpRequester(
                context,
                getUrl.toString(),
                ImmutableMultimap.of(),
                headers,
                null,
                null,
                HTTPMethod.GET,
                authInfo,
                null,
                true);

        int responseCode = -1;
        InputStream stream = null;
        IOException exception = null;
        try {
            Response<ResponseBody> response = requester.makeRequest();
            responseCode = response.code();
            if (response.isSuccessful()) {
                stream = requester.getResponseStream(response);
            }
        } catch (IOException e) {
            exception = e;
        }

        onFinishProcessing(context, background);

        return new PostResult(responseCode, stream, exception);
    }

    private boolean getInternal(Context context, String url, String version, AuthInfo authInfo,
                                Multimap<String, String> params, boolean background, IApiCallback handler) {
        if(!background) {
            if (isBusy()) {
                return false;
            }
            setCallInProgress(url);

            showProgressDialog(context);
        }

        //TODO: Figure out how to send GET request the right way
        StringBuilder getUrl = new StringBuilder(url);
        if (params.size() > 0) {
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entries()) {
                String delim = "&";
                if (first) {
                    delim = "?";
                    first = false;
                }
                getUrl.append(delim).append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        HashMap<String, String> headers = new HashMap<>();
        addVersionHeader(headers, version);

        ModernHttpTask getTask =
                new ModernHttpTask(context, getUrl.toString(),
                        ArrayListMultimap.create(),
                        headers,
                        authInfo);
        getTask.connect(getResponseProcessor(context, url, background, handler));
        getTask.executeParallel();

        return true;
    }

    private ConnectorWithHttpResponseProcessor<HttpResponseProcessor> getResponseProcessor(
            Context context, String url, boolean background, IApiCallback handler) {
        return new ConnectorWithHttpResponseProcessor<>() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData, String apiVersion) {
                onFinishProcessing(context, background);
                handler.processSuccess(responseCode, responseData);
            }

            @Override
            public void processClientError(int responseCode) {
                onFinishProcessing(context, background);

                String message = String.format(Locale.getDefault(), "Call:%s\nResponse code:%d", url, responseCode);
                CrashUtil.reportException(new Exception(message));

                if(responseCode == 406) {
                    //API version is too old, require app update.
                    handler.processOldApiError();
                } else {
                    //400 error
                    handler.processFailure(responseCode, null);
                }
            }

            @Override
            public void processServerError(int responseCode) {
                onFinishProcessing(context, background);

                String message = String.format(Locale.getDefault(), "Call:%s\nResponse code:%d", url, responseCode);
                CrashUtil.reportException(new Exception(message));

                //500 error for internal server error
                handler.processFailure(responseCode, null);
            }

            @Override
            public void processOther(int responseCode) {
                onFinishProcessing(context, background);

                String message = String.format(Locale.getDefault(), "Call:%s\nResponse code:%d", url, responseCode);
                CrashUtil.reportException(new Exception(message));

                handler.processFailure(responseCode, null);
            }

            @Override
            public void handleIOException(IOException exception) {
                onFinishProcessing(context, background);
                if (exception instanceof UnknownHostException) {
                    handler.processNetworkFailure();
                } else {
                    handler.processFailure(-1, exception);
                }
            }

            @Override
            public <A, B, C> void connectTask(CommCareTask<A, B, C, HttpResponseProcessor> task) {
            }

            @Override
            public void startBlockingForTask(int id) {
            }

            @Override
            public void stopBlockingForTask(int id) {
            }

            @Override
            public void taskCancelled() {
            }

            @Override
            public HttpResponseProcessor getReceiver() {
                return this;
            }

            @Override
            public void startTaskTransition() {
            }

            @Override
            public void stopTaskTransition(int taskId) {
            }

            @Override
            public void hideTaskCancelButton() {
            }
        };
    }

    private void onFinishProcessing(Context context, boolean background) {
        if(!background) {
            setCallInProgress(null);
            dismissProgressDialog(context);
        }
    }

    public static void showNetworkError(Context context) {
        Toast.makeText(context, context.getString(R.string.recovery_network_unavailable),
                Toast.LENGTH_SHORT).show();
    }

    public static void showOutdatedApiError(Context context) {
        Toast.makeText(context, context.getString(R.string.recovery_network_outdated),
                Toast.LENGTH_LONG).show();
    }

    private static final int NETWORK_ACTIVITY_ID = 7000;

    private void showProgressDialog(Context context) {
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

    private void dismissProgressDialog(Context context) {
        if (context instanceof CommCareActivity<?>) {
            Handler handler = new Handler(context.getMainLooper());
            handler.post(() -> {
                ((CommCareActivity<?>)context).dismissProgressDialogForTask(NETWORK_ACTIVITY_ID);
            });
        }
    }
}
