package org.commcare.utils;

import android.util.Log;

import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
import org.commcare.dalvik.debug.test.BuildConfig;
import org.commcare.modern.util.Pair;
import org.commcare.network.HttpUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
/**
 * @author $|-|!˅@M
 */
public class HQApi {

    private static final String TAG = HQApi.class.getSimpleName();
    private static final String FORM_UPLOAD_URL = "https://www.commcarehq.org/a/commcare-tests/receiver/";
    private static final String FORM_URL = "https://www.commcarehq.org/a/commcare-tests/api/v0.5/form/";
    private static final String CASE_URL = "https://www.commcarehq.org/a/commcare-tests/api/v0.5/case/";
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

    public static void closeExistingCases(String name, String type, String userId) {
        String query = "?name=" + name + "&case_type=" + type + "&closed=False";
        try {
            Response<ResponseBody> response = getRequest(CASE_URL, query);
            if (response.isSuccessful()) {
                JSONArray cases = new JSONObject(response.body().string())
                        .getJSONArray("objects");
                for (int i = 0; i < cases.length(); i++) {
                    JSONObject currentCase = cases.getJSONObject(i);
                    String caseId = currentCase.getString("case_id");
                    String xml = getCaseCloseXml(caseId, userId);
                    boolean isClosed = submitForm(xml).isSuccessful();
                    Log.d(TAG, "Case close response :: " + isClosed);
                }
                Log.d(TAG, "Closed all existing cases");
            } else {
                Log.d(TAG, "Response was unsuccessful : " + response.body());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON parsing failed with exception : " + e.getMessage());
        }
    }

    private static Response<ResponseBody> submitForm(String payload) throws IOException {
        RequestBody requestFile = RequestBody.create(MediaType.parse("text/xml"), payload);
        List<MultipartBody.Part> parts = new ArrayList<>();
        parts.add(MultipartBody.Part.createFormData("xml_submission_file", "case_close.xml", requestFile));
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(BuildConfig.HQ_API_USERNAME, BuildConfig.HQ_API_PASSWORD);
        CommCareNetworkService networkService =
                CommCareNetworkServiceGenerator.createCommCareNetworkService(
                        HttpUtils.getCredential(authInfo),
                        true,
                        true);
        return networkService.makeMultipartPostRequest(FORM_UPLOAD_URL, new HashMap<>(), new HashMap<>(), parts).execute();
    }

    private static String getCaseCloseXml(String caseId, String userId) {
        String time = "2018-01-15T07:41:01.615143Z";
        String userName = BuildConfig.HQ_API_USERNAME;
        String uid = UUID.randomUUID().toString();
        return "<?xml version='1.0' ?>" + "\n" +
                        "<system version=\"1\" uiVersion=\"1\" xmlns=\"http://commcarehq.org/case\" xmlns:orx=\"http://openrosa.org/jr/xforms\">" + "\n" + "\t" +
                        "<orx:meta xmlns:cc=\"http://commcarehq.org/xforms\">" + "\n" + "\t" + "\t" +
                        "<orx:deviceID />" + "\n" + "\t" + "\t" +
                        "<orx:timeStart>" + time + "</orx:timeStart>" + "\n" + "\t" + "\t" +
                        "<orx:timeEnd>" + time + "</orx:timeEnd>" + "\n" + "\t" + "\t" +
                        "<orx:username>" + userName + "</orx:username>" + "\n" + "\t" + "\t" +
                        "<orx:userID>" + userId + "</orx:userID>" + "\n" + "\t" + "\t" +
                        "<orx:instanceID>" + uid + "</orx:instanceID>" + "\n" + "\t" + "\t" +
                        "<cc:appVersion />" + "\n" + "\t" +
                        "</orx:meta>" + "\n" + "\t" +
                        "<case case_id=\"" + caseId + "\"" + "\n" + "\t" + "\t" +
                        "date_modified=\"" + time + "\"" + "\n" + "\t" + "\t" +
                        "user_id=\"" + userId + "\"" + "\n" + "\t" + "\t" +
                        "xmlns=\"http://commcarehq.org/case/transaction/v2\">" + "\n" + "\t" + "\t" + "\t" +
                        "<close/>" + "\n" + "\t" +
                        "</case>" + "\n" +
                        "</system>";
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
