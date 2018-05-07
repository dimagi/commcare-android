package org.commcare.network;

import android.content.Context;

import org.commcare.core.network.AuthenticationInterceptor;
import org.commcare.core.network.ModernHttpRequester;
import org.commcare.core.network.bitcache.BitCache;
import org.commcare.core.network.bitcache.BitCacheFactory;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCacheDirSetup;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;

import javax.net.ssl.SSLPeerUnverifiedException;

import okhttp3.ResponseBody;
import retrofit2.Response;

/**
 * @author ctsims
 */
public abstract class HttpCalloutTask<R> extends CommCareTask<Object, String, HttpCalloutTask.HttpCalloutOutcomes, R> {

    public enum HttpCalloutOutcomes {
        NetworkFailure,
        BadResponse,
        AuthFailed,
        UnknownError,
        BadCertificate,
        Success,
        NetworkFailureBadPassword,
        IncorrectPin,
        AuthOverHttp
    }

    private final Context c;

    public HttpCalloutTask(Context c) {
        this.c = c;

        TAG = HttpCalloutTask.class.getSimpleName();
    }

    protected Context getContext() {
        return c;
    }


    @Override
    protected HttpCalloutOutcomes doTaskBackground(Object... params) {
        HttpCalloutOutcomes preHttpOutcome = doSetupTaskBeforeRequest();
        if (preHttpOutcome != null) {
            return preHttpOutcome;
        }

        //Since we can proceed with the task either way, but we 
        //still wanna know whether it failed
        boolean calloutFailed = false;
        if (shouldMakeHttpCallout()) {
            HttpCalloutOutcomes outcome;

            try {
                Response response = doHttpRequest();

                int responseCode = response.code();

                if (responseCode >= 200 && responseCode < 300) {
                    outcome = doResponseSuccess(response);
                } else if (responseCode == 401) {
                    outcome = doResponseAuthFailed(response);
                } else {
                    outcome = doResponseOther(response);
                }
            } catch (UnknownHostException e) {
                outcome = HttpCalloutOutcomes.NetworkFailure;
            } catch (SSLPeerUnverifiedException e) {
                // Couldn't get a valid SSL certificate
                outcome = HttpCalloutOutcomes.BadCertificate;
            } catch (AuthenticationInterceptor.PlainTextPasswordException e) {
                e.printStackTrace();
                outcome = HttpCalloutOutcomes.AuthOverHttp;
            } catch (IOException e) {
                //This is probably related to local files, actually
                e.printStackTrace();
                outcome = HttpCalloutOutcomes.NetworkFailure;
            }

            //If we needed the callout to succeed and it didn't, return our failure.
            if (outcome != HttpCalloutOutcomes.Success) {
                //TODO:Cleanup?
                if (calloutSuccessRequired()) {
                    return outcome;
                } else {
                    calloutFailed = true;
                }
            } else {
                if (!processSuccessfulRequest()) {
                    return HttpCalloutOutcomes.BadResponse;
                }
            }
        }

        // So either we didn't need our our HTTP callout or we succeeded. Either way, move on
        // to the next step
        return doPostCalloutTask(calloutFailed);
    }

    protected boolean processSuccessfulRequest() {
        return true;
    }

    protected HttpCalloutOutcomes doSetupTaskBeforeRequest() {
        return null;
    }

    protected abstract Response<ResponseBody> doHttpRequest() throws IOException;

    protected HttpCalloutOutcomes doResponseSuccess(Response<ResponseBody> response) throws IOException {
        beginResponseHandling(response);

        InputStream input = cacheResponseOpenHandle(response);

        TransactionParserFactory factory = getTransactionParserFactory();

        //this is _really_ coupled, but we'll tolerate it for now because of the absurd performance gains
        try {
            DataModelPullParser parser = new DataModelPullParser(input, factory, true, false);
            parser.parse();
            return HttpCalloutOutcomes.Success;

            //TODO: These are not great, long term
        } catch (InvalidStructureException ise) {
            ise.printStackTrace();
            Logger.log(LogTypes.TYPE_USER, "Invalid response for auth keys: " + ise.getMessage());
            return HttpCalloutOutcomes.BadResponse;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_USER, "Invalid xml response for auth keys: " + e.getMessage());
            return HttpCalloutOutcomes.BadResponse;
        } catch (UnfullfilledRequirementsException e) {
            e.printStackTrace();
            Logger.log(LogTypes.TYPE_USER, "Missing requirements when fetching auth keys: " + e.getMessage());
            return HttpCalloutOutcomes.BadResponse;
        }
    }

    protected abstract TransactionParserFactory getTransactionParserFactory();

    protected InputStream cacheResponseOpenHandle(Response<ResponseBody> response) throws IOException {
        long dataSizeGuess = ModernHttpRequester.getContentLength(response);

        BitCache cache = BitCacheFactory.getCache(new AndroidCacheDirSetup(c), dataSizeGuess);
        cache.initializeCache();

        OutputStream cacheOut = cache.getCacheStream();
        StreamsUtil.writeFromInputToOutputNew(response.body().byteStream(), cacheOut);

        return cache.retrieveCache();
    }

    protected void beginResponseHandling(Response response) {
        //Nothing unless required
    }

    protected HttpCalloutOutcomes doResponseAuthFailed(Response response) {
        return HttpCalloutOutcomes.AuthFailed;
    }

    protected abstract HttpCalloutOutcomes doResponseOther(Response response);

    /**
     * Indicates whether, after doSetupTaskBeforeRequest() is executed, we actually need to
     * execute the http callout. If this is false, doSetupTaskBeforeRequest() will just be
     * followed by doPostCalloutTask()
     */
    protected abstract boolean shouldMakeHttpCallout();

    /**
     * Indicates if we need the return status of the http callout to be SUCCESS in order to proceed
     */
    protected abstract boolean calloutSuccessRequired();

    protected abstract HttpCalloutOutcomes doPostCalloutTask(boolean httpFailed);

}
