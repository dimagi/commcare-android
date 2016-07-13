package org.commcare.network;

import android.net.Uri;

import org.commcare.interfaces.HttpResponseProcessor;
import org.commcare.interfaces.ResponseStreamAccessor;
import org.commcare.utils.AndroidStreamUtil;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Make http get/post requests with query params encoded in get url or post
 * body. Delegates response to appropriate response processor callback
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpRequester implements ResponseStreamAccessor {
    /**
     * How long to wait when opening network connection in milliseconds
     */
    public static final int CONNECTION_TIMEOUT = (int)TimeUnit.SECONDS.toMillis(2);

    /**
     * How long to wait when receiving data (in milliseconds)
     */
    public static final int CONNECTION_SO_TIMEOUT = (int)TimeUnit.SECONDS.toMillis(1);

    private final boolean isPostRequest;
    private final BitCacheFactory.CacheDirSetup cacheDirSetup;
    private HttpResponseProcessor responseProcessor;
    private final URL url;
    private final HashMap<String, String> params;
    private HttpURLConnection httpConnection;

    public ModernHttpRequester(BitCacheFactory.CacheDirSetup cacheDirSetup,
                               URL url, HashMap<String, String> params,
                               User user, String domain, boolean isAuthenticatedRequest,
                               boolean isPostRequest) {
        this.isPostRequest = isPostRequest;
        this.cacheDirSetup = cacheDirSetup;
        this.params = params;
        this.url = url;

        setupAuthentication(isAuthenticatedRequest, user, domain);
    }

    public void setResponseProcessor(HttpResponseProcessor responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    private void setupAuthentication(boolean isAuth, User user, String domain) {
        if (isAuth) {
            final String username;
            if (domain != null) {
                username = user.getUsername() + "@" + domain;
            } else {
                username = user.getUsername();
            }
            final String password = user.getCachedPwd();
            if (username == null || password == null || User.TYPE_DEMO.equals(user.getUserType())) {
                String message =
                        "Trying to make authenticated http request without proper credentials";
                throw new RuntimeException(message);
            } else if (!"https".equals(url.getProtocol())) {
                throw new PlainTextPasswordException();
            } else {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password.toCharArray());
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

    public void request() {
        try {
            httpConnection = setupConnection(buildUrl());
            processResponse();
        } catch (IOException e) {
            e.printStackTrace();
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
        setupConnectionInner(httpConnection);
        if (isPostRequest) {
            httpConnection.setRequestMethod("POST");
            httpConnection.setDoOutput(true);
            buildPostPayload(httpConnection);
        } else {
            httpConnection.setRequestMethod("GET");
        }

        return httpConnection;
    }

    private static void setupConnectionInner(HttpURLConnection httpConnection) {
        httpConnection.setConnectTimeout(CONNECTION_TIMEOUT);
        httpConnection.setReadTimeout(CONNECTION_SO_TIMEOUT);
        httpConnection.setDoInput(true);
        httpConnection.setInstanceFollowRedirects(true);
    }

    private void buildPostPayload(HttpURLConnection httpConnection) throws IOException {
        String paramsString = buildUrlWithParams().getQuery();
        int bodySize = paramsString.length();
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

    private void processResponse() throws IOException {
        int responseCode = httpConnection.getResponseCode();
        processResponse(responseProcessor, responseCode, this);
    }

    public static void processResponse(HttpResponseProcessor responseProcessor,
                                       int responseCode,
                                       ResponseStreamAccessor streamAccessor) {
        if (responseCode >= 200 && responseCode < 300) {
            InputStream responseStream;
            try {
                responseStream = streamAccessor.getResponseStream();
            } catch (IOException e) {
                responseProcessor.handleIOException(e);
                return;
            }
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

    @Override
    public InputStream getResponseStream() throws IOException {
        InputStream connectionStream = httpConnection.getInputStream();

        long dataSizeGuess = setContentLengthProps(httpConnection);
        BitCache cache = BitCacheFactory.getCache(cacheDirSetup, dataSizeGuess);

        cache.initializeCache();

        OutputStream cacheOut = cache.getCacheStream();
        AndroidStreamUtil.writeFromInputToOutput(connectionStream, cacheOut);

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
