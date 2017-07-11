package org.commcare.network;

import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.commcare.CommCareApplication;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.util.CaseDBUtils;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.interfaces.HttpRequestEndpoints;
import org.commcare.logging.AndroidLogger;
import org.commcare.models.database.SqlStorage;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.preferences.DeveloperPreferences;
import org.commcare.provider.DebugControlsReceiver;
import org.commcare.utils.CredentialUtil;
import org.javarosa.core.model.User;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import okhttp3.ResponseBody;
import retrofit2.Response;


/**
 * @author ctsims
 */
public class HttpRequestGenerator implements HttpRequestEndpoints {
    private static final String TAG = HttpRequestGenerator.class.getSimpleName();

    /**
     * A possible domain that further qualifies the username of any account in use
     */
    public static final String USER_DOMAIN_SUFFIX = "cc_user_domain";

    public static final String LOG_COMMCARE_NETWORK = "commcare-network";

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

    private final Credentials credentials;
    private final String username;
    private final String password;
    private final String userType;
    private final String userId;

    /**
     * Keep track of current request to allow for early aborting
     */
    private HttpRequestBase currentRequest;

    public HttpRequestGenerator(User user) {
        this(user.getUsername(), user.getCachedPwd(), user.getUserType(), user.getUniqueId());
    }

    public HttpRequestGenerator(String username, String password) {
        this(username, password, null);
    }

    public HttpRequestGenerator(String username, String password, String userId) {
        this(username, password, null, userId);
    }

    private HttpRequestGenerator(String username, String password, String userType, String userId) {
        String domainedUsername = buildDomainUser(username);
        this.password = password = buildAppPassword(password);
        this.userType = userType;

        if (username != null && !User.TYPE_DEMO.equals(userType)) {
            this.credentials = new UsernamePasswordCredentials(domainedUsername, password);
            this.username = username;
        } else {
            this.credentials = null;
            this.username = null;
        }

        this.userId = userId;
    }

    private String buildAppPassword(String password) {
        if (DeveloperPreferences.useObfuscatedPassword()) {
            return CredentialUtil.wrap(password);
        }
        return password;
    }

    protected static String buildDomainUser(String username) {
        if (username != null && !username.contains("@")) {
            SharedPreferences prefs = CommCareApplication.instance().getCurrentApp().getAppPreferences();
            if (prefs.contains(USER_DOMAIN_SUFFIX)) {
                username += "@" + prefs.getString(USER_DOMAIN_SUFFIX, null);
            }
        }
        return username;
    }

    public static HttpRequestGenerator buildNoAuthGenerator() {
        return new HttpRequestGenerator(null, null, null, null);
    }

    @NonNull
    private Response<ResponseBody> get(@NonNull String uri, @NonNull Map params, @NonNull Map headers) throws IOException {
        CommCareNetworkService commCareNetworkService = CommCareNetworkServiceGenerator.createCommCareNetworkService(getCredentials(username, password));
        Log.d(TAG, "Fetching from: " + uri);
        return commCareNetworkService.makeGetRequest(uri, params, headers).execute();
    }

    private static String getCredentials(String username, String password) {
        if (username == null || password == null) {
            return null;
        } else {
            return okhttp3.Credentials.basic(buildDomainUser(username), password);
        }
    }

    @Override
    public Response<ResponseBody> makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws IOException {
        Map<String, String> params = new HashMap<>();

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

        return get(CommCareServerPreferences.getDataServerKey(),
                params,
                getHeaders(syncToken));
    }

    private Map<String, String> getHeaders(String lastToken) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-OpenRosa-Version", "2.0");
        if (lastToken != null) {
            headers.put("X-CommCareHQ-LastSyncToken", lastToken);
        }
        headers.put("x-openrosa-deviceid", CommCareApplication.instance().getPhoneId());
        return headers;
    }

    @Override
    public Response<ResponseBody> makeKeyFetchRequest(String baseUri, Date lastRequest) throws IOException {
        Map<String, String> params = new HashMap<>();

        if (lastRequest != null) {
            params.put("last_issued", DateUtils.formatTime(lastRequest, DateUtils.FORMAT_ISO8601));
        }

        // include IMEI in key fetch request for auditing large deployments
        params.put("device_id", CommCareApplication.instance().getPhoneId());

        return get(baseUri, params, new HashMap());
    }

    private void addHeaders(HttpRequestBase base, String lastToken) {
        //base.addHeader("Accept-Language", lang)
        base.addHeader("X-OpenRosa-Version", "2.0");
        if (lastToken != null) {
            base.addHeader("X-CommCareHQ-LastSyncToken", lastToken);
        }
        base.addHeader("x-openrosa-deviceid", CommCareApplication.instance().getPhoneId());
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
            return CaseDBUtils.computeCaseDbHash(CommCareApplication.instance().getUserStorage(ACase.STORAGE_KEY, ACase.class));
        }
    }

    @Override
    public HttpResponse postData(String url, MultipartEntity entity) throws ClientProtocolException, IOException {
        // setup client
        HttpClient httpclient = client();

        //If we're going to try to post with no credentials, we need to be explicit about the fact that we're
        //not ready
        if (credentials == null) {
            url = Uri.parse(url).buildUpon().appendQueryParameter(AUTH_REQUEST_TYPE, AUTH_REQUEST_TYPE_NO_AUTH).build().toString();
        }

        if (User.TYPE_DEMO.equals(userType)) {
            url = Uri.parse(url).buildUpon().appendQueryParameter(SUBMIT_MODE, SUBMIT_MODE_DEMO).build().toString();
        }

        HttpPost httppost = new HttpPost(url);

        httppost.setEntity(entity);
        addHeaders(httppost, this.getSyncToken(username));

        return execute(httpclient, httppost);
    }

    private HttpClient client() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, ModernHttpRequester.CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, ModernHttpRequester.CONNECTION_SO_TIMEOUT);
        HttpClientParams.setRedirecting(params, true);

        DefaultHttpClient client = new DefaultHttpClient(params);
        client.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);

        System.setProperty("http.keepAlive", "false");

        return client;
    }

    /**
     * Http requests are not so simple as "opening a request". Occasionally we may have to deal
     * with redirects. We don't want to just accept any redirect, though, since we may be directed
     * away from a secure connection. For now we'll only accept redirects from HTTP -> * servers,
     * or HTTPS -> HTTPS severs on the same domain
     */
    private HttpResponse execute(HttpClient client, HttpUriRequest request) throws IOException {
        HttpContext context = new BasicHttpContext();
        HttpResponse response = client.execute(request, context);

        HttpUriRequest currentReq = (HttpUriRequest)context.getAttribute(ExecutionContext.HTTP_REQUEST);
        HttpHost currentHost = (HttpHost)context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
        String currentUrl = currentHost.toURI() + currentReq.getURI();

        //Don't allow redirects _from_ https _to_ https unless they are redirecting to the same server.
        URL originalRequest = request.getURI().toURL();
        URL finalRedirect = new URL(currentUrl);
        if (!isValidRedirect(originalRequest, finalRedirect)) {
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Invalid redirect from " + originalRequest.toString() + " to " + finalRedirect.toString());
            throw new IOException("Invalid redirect from secure server to insecure server");
        }

        return response;
    }

    private static boolean isValidRedirect(URL url, URL newUrl) {
        //unless it's https, don't worry about it
        if (!url.getProtocol().equals("https")) {
            return true;
        }

        // If https, verify that we're on the same server.
        // Not being so means we got redirected from a secure link to a
        // different link, which isn't acceptable for now.
        return url.getHost().equals(newUrl.getHost());
    }

    @Override
    public Response<ResponseBody> simpleGet(String uri) throws IOException {
        Response<ResponseBody> response = get(uri, new HashMap(), getHeaders(""));
        if (response.code() == 404) {
            throw new FileNotFoundException("No Data available at URL " + uri);
        }
        return response;
    }

    @Override
    public void abortCurrentRequest() {
        if (currentRequest != null) {
            try {
                currentRequest.abort();
            } catch (Exception e) {
                Log.i(TAG, "Error thrown while aborting http: " + e.getMessage());
            }
        }
    }

    public static long getContentLength(retrofit2.Response response) {
        long contentLength = -1;
        String length = getFirstHeader(response, "Content-Length");
        try {
            contentLength = Long.parseLong(length);
        } catch (Exception e) {
            //Whatever.
        }
        return contentLength;
    }

    public static String getFirstHeader(retrofit2.Response response, String headerName) {
        List<String> headers = response.headers().values(headerName);
        if (headers.size() > 0) {
            return headers.get(0);
        }
        return null;
    }

}
