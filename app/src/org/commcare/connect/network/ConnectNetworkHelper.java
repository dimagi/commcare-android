package org.commcare.connect.network;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;

import org.commcare.core.network.ModernHttpRequester;
import org.commcare.utils.JsonExtensions;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;

/**
 * Helper class for building the request from headers and params
 *
 * @author dviggiano
 */
public class ConnectNetworkHelper {

    public static void addVersionHeader(HashMap<String, String> headers, String version) {
        if (version != null) {
            headers.put("Accept", "application/json;version=" + version);
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

    public static boolean checkForLoginFromDifferentDevice(String errorBody) {
        if (errorBody == null) {
            return false;
        }

        String errorCode;
        try {
            JSONObject json = new JSONObject(errorBody);
            errorCode = JsonExtensions.optStringSafe(json, "error_code", null);
        } catch (JSONException e) {
            //It's okay for the error body not to be JSON
            return false;
        }

        return "LOGIN_FROM_DIFFERENT_DEVICE".equals(errorCode);
    }
}
