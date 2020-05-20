package org.commcare.utils;

import android.util.Log;

import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
import org.commcare.dalvik.debug.test.BuildConfig;
import org.commcare.modern.util.Pair;
import org.commcare.network.HttpUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import okhttp3.ResponseBody;
import retrofit2.Response;
/**
 * @author $|-|!˅@M
 */
public class HQApi {

    private static final String TAG = HQApi.class.getSimpleName();
    private static final String FORM_URL = "https://www.commcarehq.org/a/commcare-tests/api/v0.5/form/";
    private static final String ATTACHMENT_BASE_URL = "https://www.commcarehq.org/a/commcare-tests/api/form/attachment/";

    public static Long getLatestFormTime() {
        try {
            Response<ResponseBody> response = getRequest(FORM_URL, null);
            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(response.body().string());
                String date =
                        jsonObject
                                .getJSONArray("objects")
                                .getJSONObject(0)
                                .getString("received_on");
                DateTime dateTime = DateTime.parseRfc3339(date);
                return dateTime.getValue();
            } else {
                Log.d(TAG, "Response was unsuccessful : " + response.body());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON parsing failed with exception : " + e.getMessage());
        }
        return null;
    }

    public static Pair<Long, Integer> getLatestFormTimeAndAttachmentCount() {
        try {
            Response<ResponseBody> response = getRequest(FORM_URL, null);
            if (response.isSuccessful()) {
                JSONObject latestForm = new JSONObject(response.body().string())
                        .getJSONArray("objects")
                        .getJSONObject(0);
                String date = latestForm.getString("received_on");
                DateTime dateTime = DateTime.parseRfc3339(date);

                String formId = latestForm.getString("id");
                JSONObject attachments = latestForm.getJSONObject("attachments");
                Iterator<String> keys = attachments.keys();
                int attachmentCount = 0;
                while (keys.hasNext()) {
                    String attachment = keys.next();
                    String attachmentUrl = ATTACHMENT_BASE_URL + formId + "/" + attachment;
                    Response<ResponseBody> attachmentResponse = getRequest(attachmentUrl, null);
                    if (attachmentResponse.isSuccessful() && attachmentResponse.body().bytes().length > 0) {
                        attachmentCount++;
                    }
                }
                return new Pair<>(dateTime.getValue(), attachmentCount);
            } else {
                Log.d(TAG, "Response was unsuccessful : " + response.body());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON parsing failed with exception : " + e.getMessage());
        }
        return null;
    }

    private static Response<ResponseBody> getRequest(String url, String query) throws IOException {
        if (query != null) {
            url += query;
        }
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(BuildConfig.HQ_API_USERNAME, BuildConfig.HQ_API_PASSWORD);
        CommCareNetworkService networkService =
                CommCareNetworkServiceGenerator.createCommCareNetworkService(
                        HttpUtils.getCredential(authInfo),
                        true,
                        true);
        return networkService.makeGetRequest(url, new HashMap<>(), new HashMap<>()).execute();
    }
}
