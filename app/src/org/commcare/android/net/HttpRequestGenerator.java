package org.commcare.android.net;

import android.content.SharedPreferences;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.util.Log;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
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
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.ACase;
import org.javarosa.core.model.User;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.cases.util.CaseDBUtils;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Date;
import java.util.Vector;

/**
 * @author ctsims
 */
public class HttpRequestGenerator {
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

    private Credentials credentials;
    PasswordAuthentication passwordAuthentication;
    private String username;

    public HttpRequestGenerator(User user) {
        this(user.getUsername(), user.getCachedPwd());
    }

    public HttpRequestGenerator(String username, String password) {
        String domainedUsername = username;

        SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();

        //TODO: We do this in a lot of places, we should wrap it somewhere
        if (prefs.contains(USER_DOMAIN_SUFFIX)) {
            domainedUsername += "@" + prefs.getString(USER_DOMAIN_SUFFIX, null);
        }


        this.credentials = new UsernamePasswordCredentials(domainedUsername, password);

        passwordAuthentication = new PasswordAuthentication(domainedUsername, password.toCharArray());
        this.username = username;
    }

    public HttpRequestGenerator() {
        //No authentication possible
    }

    public HttpResponse get(String uri) throws ClientProtocolException, IOException {
        HttpClient client = client();

        Log.d(TAG, "Fetching from: " + uri);
        HttpGet request = new HttpGet(uri);
        addHeaders(request, "");
        HttpResponse response = execute(client, request);

        //May need to manually process a valid redirect
        if (response.getStatusLine().getStatusCode() == 301) {
            String newGetUri = request.getURI().toString();
            Log.d(LOG_COMMCARE_NETWORK, "Following valid redirect from " + uri.toString() + " to " + newGetUri);
            request.abort();

            //Make a new response to the redirect
            request = new HttpGet(newGetUri);
            addHeaders(request, "");
            response = execute(client, request);
        }

        return response;

    }

    public HttpResponse makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws ClientProtocolException, IOException {
        HttpClient client = client();

        Uri serverUri = Uri.parse(baseUri);
        String vparam = serverUri.getQueryParameter("version");
        if (vparam == null) {
            serverUri = serverUri.buildUpon().appendQueryParameter("version", "2.0").build();
        }

        String syncToken = null;
        if (includeStateFlags) {
            syncToken = getSyncToken(username);
            String digest = getDigest();

            if (syncToken != null) {
                serverUri = serverUri.buildUpon().appendQueryParameter("since", syncToken).build();
            }
            if (digest != null) {
                serverUri = serverUri.buildUpon().appendQueryParameter("state", "ccsh:" + digest).build();
            }
        }

        //Add items count to fetch request
        serverUri = serverUri.buildUpon().appendQueryParameter("items", "true").build();

        String uri = serverUri.toString();
        Log.d(TAG, "Fetching from: " + uri);
        HttpGet request = new HttpGet(uri);
        AndroidHttpClient.modifyRequestToAcceptGzipResponse(request);
        addHeaders(request, syncToken);
        return execute(client, request);
    }

    public HttpResponse makeKeyFetchRequest(String baseUri, Date lastRequest) throws ClientProtocolException, IOException {
        HttpClient client = client();


        Uri url = Uri.parse(baseUri);

        if (lastRequest != null) {
            url = url.buildUpon().appendQueryParameter("last_issued", DateUtils.formatTime(lastRequest, DateUtils.FORMAT_ISO8601)).build();
        }

        HttpGet get = new HttpGet(url.toString());

        return execute(client, get);
    }

    private void addHeaders(HttpRequestBase base, String lastToken) {
        //base.addHeader("Accept-Language", lang)
        base.addHeader("X-OpenRosa-Version", "1.0");
        if (lastToken != null) {
            base.addHeader("X-CommCareHQ-LastSyncToken", lastToken);
        }
        base.addHeader("x-openrosa-deviceid", CommCareApplication._().getPhoneId());
    }

    public String getSyncToken(String username) {
        if (username == null) {
            return null;
        }
        SqlStorage<User> storage = CommCareApplication._().getUserStorage(User.STORAGE_KEY, User.class);
        Vector<Integer> users = storage.getIDsForValue(User.META_USERNAME, username);
        //should be exactly one user
        if (users.size() != 1) {
            return null;
        }

        return storage.getMetaDataFieldForRecord(users.firstElement(), User.META_SYNC_TOKEN);
    }

    private String getDigest() {
        return CaseDBUtils.computeHash(CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class));
    }

    public HttpResponse postData(String url, MultipartEntity entity) throws ClientProtocolException, IOException {
        // setup client
        HttpClient httpclient = client();

        //If we're going to try to post with no credentials, we need to be explicit about the fact that we're 
        //not ready 
        if (credentials == null) {
            url = Uri.parse(url).buildUpon().appendQueryParameter(AUTH_REQUEST_TYPE, AUTH_REQUEST_TYPE_NO_AUTH).build().toString();
        }

        HttpPost httppost = new HttpPost(url);

        httppost.setEntity(entity);
        addHeaders(httppost, this.getSyncToken(username));

        return execute(httpclient, httppost);
    }

    private HttpClient client() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, GlobalConstants.CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, GlobalConstants.CONNECTION_SO_TIMEOUT);
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

        HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
        HttpHost currentHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
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

    public static boolean isValidRedirect(URL url, URL newUrl) {
        //unless it's https, don't worry about it
        if (!url.getProtocol().equals("https")) {
            return true;
        }

        //if it is, verify that we're on the same server.
        if (url.getHost().equals(newUrl.getHost())) {
            return true;
        } else {
            //otherwise we got redirected from a secure link to a different
            //link, which isn't acceptable for now.
            return false;
        }
    }


    /**
     * TODO: At some point in the future this kind of division will be more central
     * but this generates an input stream for a URL using the best package for your
     * application
     *
     * @return a Stream to that URL
     */
    public InputStream simpleGet(URL url) throws IOException {

        // only for versions past gingerbread use the HttpURLConnection
        if (android.os.Build.VERSION.SDK_INT > 11) {

            if (passwordAuthentication != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return passwordAuthentication;
                    }
                });
            }

            int responseCode = -1;

            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            setup(con);
            // Start the query
            con.connect();

            try {
                responseCode = con.getResponseCode();
                //It's possible we're getting redirected from http to https
                //if so, we need to handle it explicitly
                if (responseCode == 301) {
                    //only allow one level of redirection here for now.    
                    Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Attempting 1 stage redirect from " + url.toString() + " to " + con.getURL().toString());
                    URL newUrl = new URL(con.getHeaderField("Location"));
                    con.disconnect();
                    con = (HttpURLConnection) newUrl.openConnection();
                    setup(con);
                    con.connect();
                }

                //Don't allow redirects _from_ https _to_ https unless they are redirecting to the same server.
                if (!HttpRequestGenerator.isValidRedirect(url, con.getURL())) {
                    Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Invalid redirect from " + url.toString() + " to " + con.getURL().toString());
                    throw new IOException("Invalid redirect from secure server to insecure server");
                }

                return con.getInputStream();
            } catch (IOException e) {
                if (e.getMessage().toLowerCase().contains("authentication") || responseCode == 401) {
                    //Android http libraries _suuuuuck_, let's try apache.
                } else {
                    throw e;
                }
            }
        }

        //On earlier versions of android use the apache libraries, they work much much better.

        Log.i(LOG_COMMCARE_NETWORK, "Falling back to Apache libs for network request");
        HttpResponse get = get(url.toString());

        if (get.getStatusLine().getStatusCode() == 404) {
            throw new FileNotFoundException("No Data available at URL " + url.toString());
        }

        //TODO: Double check response code
        return get.getEntity().getContent();
    }

    private void setup(HttpURLConnection con) throws IOException {
        con.setConnectTimeout(GlobalConstants.CONNECTION_TIMEOUT);
        con.setReadTimeout(GlobalConstants.CONNECTION_SO_TIMEOUT);
        con.setRequestMethod("GET");
        con.setDoInput(true);
        con.setInstanceFollowRedirects(true);
    }

}
