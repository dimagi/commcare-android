/**
 * 
 */
package org.commcare.android.tasks.templates;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.bitcache.BitCache;
import org.commcare.android.util.bitcache.BitCacheFactory;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.services.Logger;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public abstract class HttpCalloutTask<R> extends CommCareTask<Void, String, org.commcare.android.tasks.templates.HttpCalloutTask.HttpCalloutOutcomes, R>{
    
    public enum HttpCalloutOutcomes {
        NetworkFailure,
        BadResponse,
        AuthFailed,
        UnkownError,
        BadCertificate,
        Success,
        NetworkFailureBadPassword
    }
    
    Context c;
    
    public HttpCalloutTask(Context c) {
        this.c = c;
    }
    
    protected Context getContext() {
        return c;
    }
    
    
    @Override
    protected HttpCalloutOutcomes doTaskBackground(Void... params) {
        HttpCalloutOutcomes preHttpOutcome = doSetupTaskBeforeRequest();
        if(preHttpOutcome != null) {
            return preHttpOutcome;
        }
        
        //Since we can proceed with the task either way, but we 
        //still wanna know whether it failed
        boolean calloutFailed  = false;
        if(HttpCalloutNeeded()) {
            HttpCalloutOutcomes outcome;

            try {

                HttpResponse response = doHttpRequest();
                
                int responseCode = response.getStatusLine().getStatusCode();
                
                if(responseCode >= 200 && responseCode < 300) {
                    outcome = doResponseSuccess(response);
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
            } catch(SSLPeerUnverifiedException e){
                // Couldn't get a valid SSL certificate
                outcome = HttpCalloutOutcomes.BadCertificate;
            } catch (IOException e) {
                //This is probably related to local files, actually 
                outcome = HttpCalloutOutcomes.NetworkFailure;
            } finally {
                
            }
            
            //If we needed the callout to succeed and it didn't, return our failure.
            if(outcome != HttpCalloutOutcomes.Success) {
                //TODO:Cleanup?
                if(HttpCalloutRequired()) {
                    return outcome;
                } else {
                    calloutFailed = true;
                }
            } else {
                if(!processSuccesfulRequest()) {
                    return HttpCalloutOutcomes.BadResponse;
                }
            }
        }
        
        //So either we didn't need our our HTTP callout or we succeeded. Either way, move on
        //to the next step
    
        return doPostCalloutTask(calloutFailed);
    }
    
    protected boolean processSuccesfulRequest() {
        return true;
    }

    /**
     * 
     * @return
     */
    protected HttpCalloutOutcomes doSetupTaskBeforeRequest() {
        return null;
    }
    
    protected abstract HttpResponse doHttpRequest() throws ClientProtocolException, IOException;
    
    protected HttpCalloutOutcomes doResponseSuccess(HttpResponse response) throws IOException {
        beginResponseHandling(response);
        
        InputStream input = cacheResponseOpenHandle(response);
        
        TransactionParserFactory factory = getTransactionParserFactory();
        
        //this is _really_ coupled, but we'll tolerate it for now because of the absurd performance gains
        try {
            DataModelPullParser parser = new DataModelPullParser(input, factory, true, false);
            parser.parse();
            return HttpCalloutOutcomes.Success;
            
            //TODO: These are not great, long term
        } catch(InvalidStructureException ise) {
            ise.printStackTrace();
            Logger.log(AndroidLogger.TYPE_USER, "Invalid response for auth keys: " + ise.getMessage());
            return HttpCalloutOutcomes.BadResponse;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_USER, "Invalid xml response for auth keys: " + e.getMessage());
            return HttpCalloutOutcomes.BadResponse;
        } catch (UnfullfilledRequirementsException e) {
            e.printStackTrace();
            Logger.log(AndroidLogger.TYPE_USER, "Missing requirements when fetching auth keys: " + e.getMessage());
            return HttpCalloutOutcomes.BadResponse;
        } finally {
            
        }
    }
    
    protected abstract TransactionParserFactory getTransactionParserFactory();
    
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
        return HttpCalloutOutcomes.AuthFailed;
    }
    
    protected abstract HttpCalloutOutcomes doResponseOther(HttpResponse response);
    
    
    protected abstract boolean HttpCalloutNeeded();
    
    protected abstract boolean HttpCalloutRequired();
    
    protected abstract HttpCalloutOutcomes doPostCalloutTask(boolean httpFailed);
    
}
