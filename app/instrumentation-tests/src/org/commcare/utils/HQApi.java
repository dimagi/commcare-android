package org.commcare.utils;

import android.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.CommCareNetworkService;
import org.commcare.core.network.CommCareNetworkServiceGenerator;
import org.commcare.dalvik.BuildConfig;
import org.commcare.modern.util.Pair;
import org.commcare.network.HttpUtils;
import org.joda.time.DateTime;
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

    private static final String BASE_URL = "https://www.commcarehq.org/a/commcare-tests/";

    private static final String FORM_UPLOAD_URL = BASE_URL + "receiver/";
    private static final String USER_URL = BASE_URL + "api/v0.5/user/%s/";
    private static final String FORM_URL = BASE_URL + "api/v0.5/form/";
    private static final String CASE_URL = BASE_URL + "api/v0.5/case/";
    private static final String ATTACHMENT_BASE_URL = BASE_URL + "api/form/attachment/";
    private static final String FIXTURE_UPLOAD_URL = BASE_URL + "fixtures/fixapi/";

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
                return new DateTime(date).getMillis();
            } else {
                Log.d(TAG, "Response was unsuccessful : " + response.body().string());
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
                long time = new DateTime(date).getMillis();
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
                return new Pair<>(time, attachmentCount);
            } else {
                Log.d(TAG, "Response was unsuccessful : " + response.body().string());
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
                    boolean isClosed = submitForm(xml);
                    Log.d(TAG, "Case close response :: " + isClosed);
                }
                Log.d(TAG, "Closed all existing cases");
            } else {
                Log.d(TAG, "Response was unsuccessful : " + response.body().string());
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON parsing failed with exception : " + e.getMessage());
        }
    }

    public static void addUserInGroup(String userId, String groupId) {
        try {
            boolean added = true;
            if (!isUserPresentInGroup(userId, groupId)) {
                added = updateUser(userId, groupId);
            }
            Log.d(TAG, "User adding in group is successfull :: " + added);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON parsing failed with exception : " + e.getMessage());
        }
    }

    public static void removeUserFromGroup(String userId, String groupId) {
        try {
            boolean removed = true;
            if (isUserPresentInGroup(userId, groupId)) {
                removed = updateUser(userId, null);
            }
            Log.d(TAG, "Is user present in group :: " + !removed);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
            Log.d(TAG, "JSON parsing failed with exception : " + e.getMessage());
        }
    }

    public static void uploadFixture(String fixtureName) {
        ClassLoader classLoader = InstrumentationRegistry.getInstrumentation().getTargetContext().getClassLoader();
        RequestBody requestFile = new InputStreamRequestBody(MediaType.parse("text/xlsx"), classLoader, fixtureName);
        List<MultipartBody.Part> parts = new ArrayList<>();
        parts.add(MultipartBody.Part.createFormData("file-to-upload", fixtureName, requestFile));
        parts.add(MultipartBody.Part.createFormData("replace", "true"));

        CommCareNetworkService networkService = createTestNetworkService();
        Response<ResponseBody> response;
        try {
            response = networkService.makeMultipartPostRequest(FIXTURE_UPLOAD_URL, new HashMap<>(), new HashMap<>(), parts).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "Uploading Fixture succeeded :: " + response.body().string());
            } else {
                Log.d(TAG, "Uploading Fixture failed :: " +
                        (response.body() != null ? response.body().string() : response.errorBody().string()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void createUser(String name, String password) {
        String url = BASE_URL + "api/v0.5/user/";
        try {
            String userName = name + "@commcare-tests.commcarehq.org";
            String payload = "{\"first_name\": \"Temporary\", " +
                    "\"last_name\": \"User\", " +
                    "\"username\": \"" + userName + "\", " +
                    "\"password\": \"" + password + "\" }";
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), payload);
            Response<ResponseBody> response = postRequest(url, null, requestBody);
            if (response.isSuccessful()) {
                Log.d(TAG, "Create user succeeded with response :: " + response.body().string());
            } else {
                Log.d(TAG, "Create user failed with response :: " + response.errorBody().string());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteUser(String name) {
        String userName = name + "@commcare-tests.commcarehq.org";
        String url = BASE_URL + "api/v0.5/user/";
        try {
            Response<ResponseBody> response = getRequest(url, null);
            if (response.isSuccessful()) {
                JSONArray userList = new JSONObject(response.body().string())
                        .getJSONArray("objects");
                for (int i = 0; i < userList.length(); i++) {
                    JSONObject user = userList.getJSONObject(i);
                    if (userName.equals(user.getString("username"))) {
                        String userId = user.getString("id");

                        return;
                    }
                }
                Log.d(TAG, "User not found");
            } else {
                Log.d(TAG, "Getting user by id failed with response :: " + response.body().string());
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void deleteUserById(String userId) {
        String url = String.format(USER_URL, userId);
        CommCareNetworkService networkService = createTestNetworkService();
        Response<ResponseBody> response = null;
        try {
            response = networkService.makeDeleteRequest(url, new HashMap<>(), new HashMap<>()).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "User successfully deleted :: " + response.body().string());
            } else {
                Log.d(TAG, "User deletion failed with response :: " + response.errorBody().string());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean updateUser(String userId, String groupId) {
        String url = String.format(USER_URL, userId);
        String payload;
        if (groupId == null) {
            payload = "{\"groups\": []}";
        } else {
            payload = "{\"groups\": [\"" + groupId + "\"]}";
        }
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), payload);

        CommCareNetworkService networkService = createTestNetworkService();
        Response<ResponseBody> response = null;
        try {
            response = networkService.makePutRequest(url, requestBody).execute();
            return response.isSuccessful();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.body().close();
            }
        }
        return false;
    }

    private static boolean isUserPresentInGroup(String userId, String groupId) throws IOException, JSONException {
        JSONArray array = getUserGroups(userId);
        for (int i = 0; i < array.length(); i++) {
            String userGrp = array.getString(i);
            if (groupId.equals(userGrp)) {
                return true;
            }
        }
        return false;
    }

    private static JSONArray getUserGroups(String userId) throws IOException, JSONException {
        String url = String.format(USER_URL, userId);
        String param = "?format=json";
        Response<ResponseBody> response = getRequest(url, param);
        return new JSONObject(response.body().string()).getJSONArray("groups");
    }

    private static boolean submitForm(String payload) {
        RequestBody requestFile = RequestBody.create(MediaType.parse("text/xml"), payload);
        List<MultipartBody.Part> parts = new ArrayList<>();
        parts.add(MultipartBody.Part.createFormData("xml_submission_file", "case_close.xml", requestFile));
        CommCareNetworkService networkService = createTestNetworkService();
        Response<ResponseBody> response = null;
        try {
            response = networkService.makeMultipartPostRequest(FORM_UPLOAD_URL, new HashMap<>(), new HashMap<>(), parts).execute();
            return response.isSuccessful();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.body().close();
            }
        }
        return false;
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
        CommCareNetworkService networkService = createTestNetworkService();
        return networkService.makeGetRequest(url, new HashMap<>(), new HashMap<>()).execute();
    }

    private static Response<ResponseBody> postRequest(String url, String query, RequestBody body) throws IOException {
        if (query != null) {
            url += query;
        }
        CommCareNetworkService networkService = createTestNetworkService();
        return networkService.makePostRequest(url, new HashMap<>(), new HashMap<>(), body).execute();
    }

    private static CommCareNetworkService createTestNetworkService() {
        AuthInfo authInfo = new AuthInfo.ProvidedAuth(BuildConfig.HQ_API_USERNAME, BuildConfig.HQ_API_PASSWORD);
        CommCareNetworkService networkService =
                CommCareNetworkServiceGenerator.createCommCareNetworkService(
                        HttpUtils.getCredential(authInfo),
                        true,
                        true);
        return networkService;
    }
}
