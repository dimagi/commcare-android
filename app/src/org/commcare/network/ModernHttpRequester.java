package org.commcare.network;

import android.content.Context;
import android.net.Uri;

import org.commcare.CommCareApplication;
import org.commcare.interfaces.HttpResponseProcessor;
import org.commcare.utils.AndroidStreamUtil;
import org.commcare.utils.GlobalConstants;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.utils.bitcache.BitCache;
import org.commcare.utils.bitcache.BitCacheFactory;
import org.javarosa.core.model.User;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.Hashtable;
import java.util.Map;

/**
 * Make http get/post requests with query params encoded in get url or post
 * body. Delegates response to appropriate response processor callback
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpRequester {
    private final boolean isPostRequest;
    private final Context context;
    private HttpResponseProcessor responseProcessor;
    protected final URL url;
    private final Hashtable<String, String> params;

    public ModernHttpRequester(Context context, URL url,
                               Hashtable<String, String> params,
                               boolean isAuthenticatedRequest,
                               boolean isPostRequest) {
        this.isPostRequest = isPostRequest;
        this.context = context;
        this.params = params;
        this.url = url;

        setupAuthentication(isAuthenticatedRequest);
    }

    public void setResponseProcessor(HttpResponseProcessor responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    private void setupAuthentication(boolean isAuth) {
        if (isAuth) {
            User u = getCurrentUser();
            final String username = u.getUsername();
            final String password = u.getCachedPwd();
            if (username == null || password == null || User.TYPE_DEMO.equals(u.getUserType())) {
                String message =
                        "Trying to make authenticated http request without proper credentials";
                throw new RuntimeException(message);
            } else if (!"https".equals(url.getProtocol())) {
                throw new PlainTextPasswordException();
            } else {
                // make authenticated requests
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                HttpRequestGenerator.buildDomainUser(username),
                                password.toCharArray());
                    }
                });
            }
        } else {
            // clear any prior set authenticator to make unauthed requests
            Authenticator.setDefault(null);
        }
    }

    public static class PlainTextPasswordException extends RuntimeException {
    }

    private static User getCurrentUser() {
        try {
            return CommCareApplication._().getSession().getLoggedInUser();
        } catch (SessionUnavailableException sue) {
            throw new RuntimeException("Can't find user to make authenticated http request.");
        }
    }

    public void request() {
        HttpURLConnection httpConnection = null;
        try {
            httpConnection = setupConnection(buildUrl());
            processResponse(httpConnection);
        } catch (IOException e) {
            responseProcessor.handleIOException(e);
        } finally {
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
        }
    }

    private URL buildUrl() throws IOException {
        if (isPostRequest) {
            return url;
        } else {
            return buildUrlWithParams();
        }
    }

    protected HttpURLConnection setupConnection(URL builtUrl) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection)builtUrl.openConnection();
        if (isPostRequest) {
            setupConnectionInner(httpConnection);
            httpConnection.setRequestMethod("POST");
            httpConnection.setDoOutput(true);
            buildPostPayload(httpConnection);
        } else {
            setupConnectionInner(httpConnection);
            httpConnection.setRequestMethod("GET");
        }

        return httpConnection;
    }

    private static void setupConnectionInner(HttpURLConnection httpConnection) {
        httpConnection.setConnectTimeout(GlobalConstants.CONNECTION_TIMEOUT);
        httpConnection.setReadTimeout(GlobalConstants.CONNECTION_SO_TIMEOUT);
        httpConnection.setDoInput(true);
        httpConnection.setInstanceFollowRedirects(true);
    }

    private void buildPostPayload(HttpURLConnection httpConnection) throws IOException {
        String paramsString = buildUrlWithParams().getQuery();
        int bodySize = paramsString.length();
        httpConnection.setFixedLengthStreamingMode(bodySize);
        httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        httpConnection.setRequestProperty("Content-Length", bodySize + "");
        // write to connection
        OutputStream os = httpConnection.getOutputStream();
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(os, "UTF-8"));
        writer.write(paramsString);
        writer.flush();
        writer.close();
        os.close();
    }

    private URL buildUrlWithParams() throws MalformedURLException {
        Uri.Builder b = Uri.parse(url.toString()).buildUpon();
        for (Map.Entry<String, String> param : params.entrySet()) {
            b.appendQueryParameter(param.getKey(), param.getValue());
        }
        return new URL(b.build().toString());
    }

    private void processResponse(HttpURLConnection con) throws IOException {
        int responseCode = con.getResponseCode();
        processResponse(responseProcessor, responseCode, getResponseStream(con));
    }

    public static void processResponse(HttpResponseProcessor responseProcessor,
                                       int responseCode,
                                       InputStream responseStream) {
        if (responseCode >= 200 && responseCode < 300) {
            responseProcessor.processSuccess(responseCode, responseStream);
        } else if (responseCode >= 300 && responseCode < 400) {
            responseProcessor.processRedirection(responseCode);
        } else if (responseCode >= 400 && responseCode < 500) {
            responseProcessor.processClientError(responseCode);
        } else if (responseCode >= 500 && responseCode < 600) {
            responseProcessor.processServerError(responseCode);
        } else {
            responseProcessor.processOther(responseCode);
        }
    }

    private InputStream getResponseStream(HttpURLConnection con) throws IOException {
        long dataSizeGuess = setContentLengthProps(con);
        BitCache cache = BitCacheFactory.getCache(context, dataSizeGuess);

        cache.initializeCache();

        OutputStream cacheOut = cache.getCacheStream();
        AndroidStreamUtil.writeFromInputToOutput(con.getInputStream(), cacheOut);

        return cache.retrieveCache();
    }

    private static long setContentLengthProps(HttpURLConnection httpConnection) {
        long dataSizeGuess = -1;
        String lengthHeader = httpConnection.getHeaderField("Content-Length");
        if (lengthHeader != null) {
            try {
                dataSizeGuess = Integer.parseInt(lengthHeader);
            } catch (Exception e) {
                dataSizeGuess = -1;
            }
        }
        return dataSizeGuess;
    }
}
