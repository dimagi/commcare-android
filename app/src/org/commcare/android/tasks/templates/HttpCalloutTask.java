/**
 * 
 */
package org.commcare.android.tasks.templates;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;

import net.sqlcipher.database.SQLiteDatabase;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.bitcache.BitCache;
import org.commcare.android.util.bitcache.BitCacheFactory;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.xml.CommCareTransactionParserFactory;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.Logger;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.os.AsyncTask;

/**
 * @author ctsims
 *
 */
public abstract class HttpCalloutTask extends AsyncTask<Void, Integer, org.commcare.android.tasks.templates.HttpCalloutTask.HttpCalloutOutcomes>{
	
	public enum HttpCalloutOutcomes {
		CalloutUneeded,
		NetworkFailure,
		BadResponse,
		AuthFailed,
		UnkownError,
		Success
	}
	
	Context c;
	
	public HttpCalloutTask(Context c) {
		this.c = c;
	}
	
	
	@Override
	protected HttpCalloutOutcomes doInBackground(Void... params) {
		if(doSetupTaskBeforeRequest()) {
			return HttpCalloutOutcomes.CalloutUneeded;
		}
		
		HttpResponse response = doHttpRequest();
		
		int responseCode = response.getStatusLine().getStatusCode();
		
		HttpCalloutOutcomes outcome;
		
		try {
		
			if(responseCode >= 200 && responseCode < 300) {
				outcome = doResponseSuccess(response);
				if(outcome == HttpCalloutOutcomes.Success) {
					doCleanupSuccess();
					return outcome;
				}
			} else if(responseCode  == 401) {
				outcome = doResponseAuthFailed(response);
			} else {
				outcome = doResponseOther(response);
			}
		
		} catch (ClientProtocolException e) {
			//This is a general HTTP exception, basically 
			outcome = HttpCalloutOutcomes.NetworkFailure;
		} catch (UnknownHostException e) {
			//HTTP Error 
			outcome = HttpCalloutOutcomes.NetworkFailure;
		} catch (IOException e) {
			//This is probably related to local files, actually 
			outcome = HttpCalloutOutcomes.NetworkFailure;
		} finally {
			
		}
		
		doCleanupFailure();
		return outcome;
	}
	
	protected void doCleanupSuccess() {}
	protected void doCleanupFailure() {}

	/**
	 * Set up any relevant parameters,
	 * @return
	 */
	protected boolean doSetupTaskBeforeRequest() {
		return false;
	}
	
	protected abstract HttpResponse doHttpRequest();
	
	protected HttpCalloutOutcomes doResponseSuccess(HttpResponse response) throws IOException {
		beginResponseHandling(response);
		
		InputStream input = cacheResponseOpenHandle(response);
		
		CommCareTransactionParserFactory factory = getTransactionParserFactory();
		
		//this is _really_ coupled, but we'll tolerate it for now because of the absurd performance gains
		SQLiteDatabase db = CommCareApplication._().getUserDbHandle();
		try {
			db.beginTransaction();
			DataModelPullParser parser = new DataModelPullParser(input, factory);
			parser.parse();
			db.setTransactionSuccessful();
			return HttpCalloutOutcomes.Success;
			
			//TODO: These are not great, long term
		} catch(InvalidStructureException ise) {
			return HttpCalloutOutcomes.BadResponse;
		} catch (XmlPullParserException e) {
			return HttpCalloutOutcomes.BadResponse;
		} catch (UnfullfilledRequirementsException e) {
			return HttpCalloutOutcomes.BadResponse;
		} finally {
			db.endTransaction();
		}
	}
	
	protected abstract CommCareTransactionParserFactory getTransactionParserFactory();
	
	protected InputStream cacheResponseOpenHandle(HttpResponse response) throws IOException {
		int dataSizeGuess = -1;
		if(response.containsHeader("Content-Length")) {
			String length = response.getFirstHeader("Content-Length").getValue();
			try{
				dataSizeGuess = Integer.parseInt(length);
			} catch(Exception e) {
				//Whatever.
			}
		}
		
		BitCache cache = BitCacheFactory.getCache(c, dataSizeGuess);
		
		cache.initializeCache();
		
		OutputStream cacheOut = cache.getCacheStream();
		AndroidStreamUtil.writeFromInputToOutput(response.getEntity().getContent(), cacheOut);
	
		return cache.retrieveCache();
	}
	
	protected void beginResponseHandling(HttpResponse response) {
		//Nothing unless required
	}
	
	protected HttpCalloutOutcomes doResponseAuthFailed(HttpResponse response) {
		return doResponseOther(response);
	}
	
	protected abstract HttpCalloutOutcomes doResponseOther(HttpResponse response);
	

	
}
