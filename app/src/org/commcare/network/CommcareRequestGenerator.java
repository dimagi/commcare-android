package org.commcare.network;

import android.content.pm.PackageManager;
import android.net.Uri;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.ACase;
import org.commcare.core.network.AuthInfo;
import org.commcare.core.network.HTTPMethod;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.dalvik.BuildConfig;
import org.commcare.engine.cases.CaseUtils;
import org.commcare.interfaces.CommcareRequestEndpoints;
import org.commcare.models.database.SqlStorage;
import org.commcare.provider.DebugControlsReceiver;
import org.commcare.utils.SyncDetailCalculations;
import org.javarosa.core.model.User;
import org.javarosa.core.model.utils.DateUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.annotation.Nullable;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Response;


/**
 * @author ctsims
 */
public class CommcareRequestGenerator implements CommcareRequestEndpoints {

    /**
     * The type of authentication that we're capable of providing to the server (digest if this isn't present)
     */
    public static final String AUTH_REQUEST_TYPE = "authtype";

    /**
     * No Authentication will be possible, there isn't a user account to authenticate this request
     */
    public static final String AUTH_REQUEST_TYPE_NO_AUTH = "noauth";

    private static final String SUBMIT_MODE = "submit_mode";
    private static final String SUBMIT_MODE_DEMO = "demo";
    private static final String QUERY_PARAM_FORCE_LOGS = "force_logs";
    public static final String LOG_VERSION_KEY = "log_version";
    public static final String LOG_VERSION_VALUE = "2";

    // headers
    private static final String X_OPENROSA_VERSION = "X-OpenRosa-Version";
    private static final String X_COMMCAREHQ_LAST_SYNC_TOKEN = "X-CommCareHQ-LastSyncToken";
    public static final String X_COMMCAREHQ_REQUEST_SOURCE = "X-CommCareHQ-RequestSource";
    public static final String X_COMMCAREHQ_REQUEST_AGE = "X-CommCareHQ-RequestAge";
    private static final String X_OPENROSA_DEVICEID = "x-openrosa-deviceid";
    private static final String X_OPENROSA_COMMCARE_VERSION = "x-openrosa-commcare-version";

    private final String username;
    private final String password;
    private final String userType;
    private final String userId;

    @Nullable
    private ModernHttpRequester requester;

    public CommcareRequestGenerator(User user) {
        this(user.getUsername(), user.getCachedPwd(), user.getUserType(), user.getUniqueId());
    }

    public CommcareRequestGenerator(String username, String password) {
        this(username, password, null);
    }

    public CommcareRequestGenerator(String username, String password, String userId) {
        this(username, password, null, userId);
    }

    private CommcareRequestGenerator(String username, String password, String userType, String userId) {
        this.password = password;
        this.userType = userType;
        this.username = username;
        this.userId = userId;
    }

    public static CommcareRequestGenerator buildNoAuthGenerator() {
        return new CommcareRequestGenerator(null, null, null, null);
    }

    @Override
    public Response<ResponseBody> makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws IOException {
        HashMap<String, String> params = new HashMap<>();

        Uri serverUri = Uri.parse(baseUri);
        String vparam = serverUri.getQueryParameter("version");
        if (vparam == null) {
            params.put("version", "2.0");
        }

        // include IMEI in key fetch request for auditing large deployments
        params.put("device_id", CommCareApplication.instance().getPhoneId());

        if (userId != null) {
            params.put("user_id", userId);
        }

        String syncToken = null;
        if (includeStateFlags) {
            syncToken = getSyncToken(username);
            String digest = getDigest();

            if (syncToken != null) {
                params.put("since", syncToken);
            }
            if (digest != null) {
                params.put("state", "ccsh:" + digest);
            }

            int getDaysSinceSync = SyncDetailCalculations.getDaysSinceLastSync();
            if(getDaysSinceSync != -1) {
                params.put("days_since_last_sync", Integer.toString(getDaysSinceSync));
            }
        }

        //Add items count to fetch request
        params.put("items", "true");

        if (CommCareApplication.instance().shouldInvalidateCacheOnRestore()) {
            // Currently used for testing purposes only, in order to ensure that a full sync will
            // occur when we want to test one
            params.put("overwrite_cache", "true");
            // Always wipe this flag after we have used it once
            CommCareApplication.instance().setInvalidateCacheFlag(false);
        }

        requester = CommCareApplication.instance().createGetRequester(
                CommCareApplication.instance(),
                baseUri,
                params,
                getHeaders(syncToken),
                new AuthInfo.ProvidedAuth(username, password),
                null);

        return requester.makeRequest();
    }

    public static HashMap<String, String> getHeaders(String lastToken) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put(X_OPENROSA_VERSION, "3.0");
        if (lastToken != null) {
            headers.put(X_COMMCAREHQ_LAST_SYNC_TOKEN, lastToken);
        }
        headers.put(X_OPENROSA_DEVICEID, CommCareApplication.instance().getPhoneId());
        headers.put(X_OPENROSA_COMMCARE_VERSION, BuildConfig.VERSION_NAME);
        return headers;
    }

    @Override
    public Response<ResponseBody> makeKeyFetchRequest(String baseUri, @Nullable Date lastRequest) throws IOException {
        HashMap params = new HashMap<>();

        if (lastRequest != null) {
            params.put("last_issued", DateUtils.formatTime(lastRequest, DateUtils.FORMAT_ISO8601));
        }

        // include IMEI in key fetch request for auditing large deployments
        params.put("device_id", CommCareApplication.instance().getPhoneId());

        requester = CommCareApplication.instance().createGetRequester(
                CommCareApplication.instance(),
                baseUri,
                params,
                new HashMap(),
                new AuthInfo.ProvidedAuth(username, password),
                null);

        return requester.makeRequest();
    }

    private String getSyncToken(String username) {
        if (username == null) {
            return null;
        }
        SqlStorage<User> storage = CommCareApplication.instance().getUserStorage(User.STORAGE_KEY, User.class);
        Vector<Integer> users = storage.getIDsForValue(User.META_USERNAME, username);
        //should be exactly one user
        if (users.size() != 1) {
            return null;
        }

        return storage.getMetaDataFieldForRecord(users.firstElement(), User.META_SYNC_TOKEN);
    }

    private static String getDigest() {
        String fakeHash = DebugControlsReceiver.getFakeCaseDbHash();
        if (fakeHash != null) {
            // For integration tests, use fake hash to trigger 412 recovery on this sync
            return fakeHash;
        } else {
            return CaseUtils.computeCaseDbHash(
                    CommCareApplication.instance().getUserStorage(ACase.STORAGE_KEY, ACase.class));
        }
    }

    @Override
    public Response<ResponseBody> postMultipart(String url, List<MultipartBody.Part> parts, HashMap<String, String> params) throws IOException {

        HashMap<String, String> queryParams = new HashMap<>(params);

        //If we're going to try to post with no credentials, we need to be explicit about the fact that we're not ready
        if (username == null || password == null) {
            queryParams.put(AUTH_REQUEST_TYPE, AUTH_REQUEST_TYPE_NO_AUTH);
        }

        if (User.TYPE_DEMO.equals(userType)) {
            queryParams.put(SUBMIT_MODE, SUBMIT_MODE_DEMO);
            queryParams.put(AUTH_REQUEST_TYPE, AUTH_REQUEST_TYPE_NO_AUTH);
        }

        requester = CommCareApplication.instance().buildHttpRequester(
                CommCareApplication.instance(),
                url,
                queryParams,
                getHeaders(getSyncToken(username)),
                null,
                parts,
                HTTPMethod.MULTIPART_POST,
                new AuthInfo.ProvidedAuth(username, password),
                null,
                false);

        return requester.makeRequest();
    }

    @Override
    public Response<ResponseBody> simpleGet(String uri) throws IOException {
        return simpleGet(uri, new HashMap<>(), new HashMap<>());
    }

    @Override
    public Response<ResponseBody> simpleGet(String uri, Map<String, String> httpParams, Map<String, String> httpHeaders) throws IOException {
        HashMap<String, String> headers = new HashMap<>(getHeaders(null));
        headers.putAll(httpHeaders);

        ModernHttpRequester requester = CommCareApplication.instance().createGetRequester(
                CommCareApplication.instance(),
                uri,
                httpParams,
                headers,
                new AuthInfo.ProvidedAuth(username, password),
                null);

        Response<ResponseBody> response = requester.makeRequest();
        if (response.code() == 404) {
            throw new FileNotFoundException("No Data available at URL " + uri);
        }
        return response;
    }

    @Override
    public void abortCurrentRequest() {
        if (requester != null) {
            requester.cancelRequest();
        }
    }

    @Override
    public Response<ResponseBody> postLogs(String submissionUrl, List<MultipartBody.Part> parts, boolean forceLogs) throws IOException {
        HashMap<String, String> queryParams = new HashMap<>();
        queryParams.put(QUERY_PARAM_FORCE_LOGS, String.valueOf(forceLogs));
        queryParams.put(LOG_VERSION_KEY, LOG_VERSION_VALUE);
        return postMultipart(submissionUrl, parts, queryParams);
    }
}