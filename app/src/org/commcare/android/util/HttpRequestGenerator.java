/**
 * 
 */
package org.commcare.android.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Vector;

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
import org.commcare.android.database.user.models.User;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.cases.util.CaseDBUtils;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.Logger;

import android.content.SharedPreferences;
import android.net.Uri;

/**
 * @author ctsims
 *
 */
public class HttpRequestGenerator {
	

	private Credentials credentials;
	private String username;

	public HttpRequestGenerator(User user) {
		this(user.getUsername(), user.getPassword());
	}
	public HttpRequestGenerator(String username, String password) {
    	String domainedUsername = username; 
    	
		SharedPreferences prefs = CommCareApplication._().getCurrentApp().getAppPreferences();
		
		//TODO: We do this in a lot of places, we should wrap it somewhere
		if(prefs.contains("cc_user_domain")) {
			domainedUsername += "@" + prefs.getString("cc_user_domain",null);
		}

		
		this.credentials = new UsernamePasswordCredentials(domainedUsername, password);
		this.username = username;
	}
	
	public HttpRequestGenerator() {
		//No authentication possible
	}

	public HttpResponse makeCaseFetchRequest(String baseUri) throws ClientProtocolException, IOException {
		return makeCaseFetchRequest(baseUri, true);
	}
	
	public HttpResponse makeCaseFetchRequest(String baseUri, boolean includeStateFlags) throws ClientProtocolException, IOException {
		HttpClient client = client();
		
		Uri serverUri = Uri.parse(baseUri);
		String vparam = serverUri.getQueryParameter("version");
		if(vparam == null) {
			serverUri = serverUri.buildUpon().appendQueryParameter("version", "2.0").build();
		} 
		
		String syncToken = null;
		if(includeStateFlags) {
			syncToken = getSyncToken(username);
			String digest = getDigest();
		
			if(syncToken != null) {
				serverUri = serverUri.buildUpon().appendQueryParameter("since", syncToken).build();
			}
			if(digest != null) {
				serverUri = serverUri.buildUpon().appendQueryParameter("state", "ccsh:" + digest).build();
			}
		}
		
		String uri = serverUri.toString();
		System.out.println("Fetching from: " + uri);
		HttpGet request = new HttpGet(uri);
		addHeaders(request, syncToken);
		return execute(client, request);
	}
	
	public HttpResponse makeKeyFetchRequest(String baseUri, Date lastRequest) throws ClientProtocolException, IOException {
		HttpClient client = client();

		
		Uri url = Uri.parse(baseUri);
		
		if(lastRequest != null) {
			url = url.buildUpon().appendQueryParameter("last_issued", DateUtils.formatTime(lastRequest, DateUtils.FORMAT_ISO8601)).build();
		}
		
		HttpGet get = new HttpGet(url.toString());
		
		return execute(client, get);
	}



	private void addHeaders(HttpRequestBase base, String lastToken){
		//base.addHeader("Accept-Language", lang)
		base.addHeader("X-OpenRosa-Version", "1.0");
		if(lastToken != null) {
			base.addHeader("X-CommCareHQ-LastSyncToken",lastToken);
		}
		base.addHeader("x-openrosa-deviceid", CommCareApplication._().getPhoneId());
	}
	
	public String getSyncToken(String username) {
		if(username == null) { 
			return null;
		}
		SqlStorage<User> storage = CommCareApplication._().getUserStorage(User.class);
		Vector<Integer> users = storage.getIDsForValue(User.META_USERNAME, username);
		//should be exactly one user
		if(users.size() != 1) {
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
	 * 
	 * @param client
	 * @param request
	 * @return
	 */
	private HttpResponse execute(HttpClient client, HttpUriRequest request) throws IOException {
	    HttpContext context = new BasicHttpContext(); 
		HttpResponse response = client.execute(request, context);
		
	    HttpUriRequest currentReq = (HttpUriRequest) context.getAttribute(ExecutionContext.HTTP_REQUEST);
	    HttpHost currentHost = (HttpHost)  context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
	    String currentUrl = currentHost.toURI() + currentReq.getURI();
		 
		//Don't allow redirects _from_ https _to_ https unless they are redirecting to the same server.
	    URL originalRequest = request.getURI().toURL();
	    URL finalRedirect = new URL(currentUrl);
		if(!isValidRedirect(originalRequest, finalRedirect)) {
			Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Invalid redirect from " + originalRequest.toString() + " to " + finalRedirect.toString());
			throw new IOException("Invalid redirect from secure server to insecure server");
		}
		return response;
	}
	
	public static boolean isValidRedirect(URL url, URL newUrl) {
		//unless it's https, don't worry about it
		if(!url.getProtocol().equals("https")) {
			return true;
		}
		
		//if it is, verify that we're on the same server.
		if(url.getHost().equals(newUrl.getHost())) {
			return true;
		} else {
			//otherwise we got redirected from a secure link to a different
			//link, which isn't acceptable for now.
			return false;
		}
	}
}
