/**
 * 
 */
package org.commcare.android.util;

import java.io.IOException;
import java.util.Vector;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.models.User;
import org.commcare.cases.util.CaseDBUtils;
import org.commcare.dalvik.application.CommCareApplication;

import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

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
    	
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(CommCareApplication._());
		
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
		return client.execute(request);
	}
	
	public HttpResponse makeKeyFetchRequest(String baseUri) throws ClientProtocolException, IOException {
		HttpClient client = client();

		HttpGet get = new HttpGet(baseUri);
		addHeaders(get, getSyncToken(username));
		return client.execute(get);
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
		SqlIndexedStorageUtility<User> storage = CommCareApplication._().getStorage(User.STORAGE_KEY, User.class);
		Vector<Integer> users = storage.getIDsForValue(User.META_USERNAME, username);
		//should be exactly one user
		if(users.size() != 1) {
			return null;
		}
		
		return storage.getMetaDataFieldForRecord(users.firstElement(), User.META_SYNC_TOKEN);
	}
	
	private String getDigest() {
		return CaseDBUtils.computeHash(CommCareApplication._().getStorage(ACase.STORAGE_KEY, ACase.class));
	}
	public HttpResponse postData(String url, MultipartEntity entity) throws ClientProtocolException, IOException {
        // setup client
        HttpClient httpclient = client();
        HttpPost httppost = new HttpPost(url);
		
        httppost.setEntity(entity);
        addHeaders(httppost, this.getSyncToken(username));
        
        return httpclient.execute(httppost);
	}
	
	private HttpClient client() {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, GlobalConstants.CONNECTION_TIMEOUT);
        HttpConnectionParams.setSoTimeout(params, GlobalConstants.CONNECTION_SO_TIMEOUT);
        HttpClientParams.setRedirecting(params, false);
        
        DefaultHttpClient client = new DefaultHttpClient(params);
        client.getCredentialsProvider().setCredentials(AuthScope.ANY, credentials);
        
        System.setProperty("http.keepAlive", "false");

        return client;
	}
}
