package org.commcare.network;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.v4.util.Pair;

import org.commcare.interfaces.HttpResponseProcessor;
import org.commcare.utils.AndroidStreamUtil;
import org.commcare.utils.GlobalConstants;
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
import java.util.List;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ModernHttpRequester {
    private final boolean isPostRequest;
    private final Context context;
    private final HttpResponseProcessor responseProcessor;
    private final URL url;
    private final List<Pair<String, String>> params;

    private ModernHttpRequester(String username, String password,
                                Context context, URL url,
                                HttpResponseProcessor responseProcessor,
                                List<Pair<String, String>> params,
                                String userType, boolean isPostRequest) {
        this.isPostRequest = isPostRequest;
        this.context = context;
        this.responseProcessor = responseProcessor;
        this.params = params;
        this.url = url;

        setupAuthentication(username, password, userType);
    }

    private void setupAuthentication(final String username, final String password,
                                     final String userType) {
        if (username == null || password == null || User.TYPE_DEMO.equals(userType)) {
            // clear any prior set authenticator to make unauthed requests
            Authenticator.setDefault(null);
        } else if (!"https".equals(url.getProtocol())) {
            throw new RuntimeException("Don't transmit credentials in plain text");
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
    }

    public void request() {
        HttpURLConnection httpConnection = null;
        try {
            httpConnection = setupConnection();
            httpConnection.connect();
            processResponse(httpConnection);
        } catch (IOException e) {
            responseProcessor.handleIOException(e);
        } finally {
            if (httpConnection != null) {
                httpConnection.disconnect();
            }
        }
    }

    private HttpURLConnection setupConnection() throws IOException {
        HttpURLConnection httpConnection;
        if (isPostRequest) {
            httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setRequestMethod("POST");
            buildPostPayload(httpConnection);
            httpConnection.setDoOutput(true);
        } else {
            URL urlWithQuery = buildUrlWithParams();
            httpConnection = (HttpURLConnection) urlWithQuery.openConnection();
            httpConnection.setRequestMethod("GET");
        }

        httpConnection.setConnectTimeout(GlobalConstants.CONNECTION_TIMEOUT);
        httpConnection.setReadTimeout(GlobalConstants.CONNECTION_SO_TIMEOUT);
        httpConnection.setDoInput(true);
        httpConnection.setInstanceFollowRedirects(true);
        return httpConnection;
    }

    private void buildPostPayload(HttpURLConnection httpConnection) throws IOException {
        OutputStream os = httpConnection.getOutputStream();
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(os, "UTF-8"));
        String paramsString = buildUrlWithParams().getQuery();
        writer.write(paramsString);
        writer.flush();
        writer.close();
        os.close();
        httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        httpConnection.setRequestProperty("Content-Length", paramsString.length() + "");
    }

    private URL buildUrlWithParams() throws MalformedURLException {
        Uri.Builder b = Uri.parse(url.toString()).buildUpon();
        for (Pair<String, String> param : params) {
            b.appendQueryParameter(param.first, param.second);
        }
        return new URL(b.build().toString());
    }

    private void processResponse(HttpURLConnection con) throws IOException {
        int responseCode = con.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            responseProcessor.processSuccess(responseCode, getResponseStream(con));
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
        if (dataSizeGuess == -1) {
            // 0 uses default chunk size
            httpConnection.setChunkedStreamingMode(0);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                httpConnection.setFixedLengthStreamingMode(dataSizeGuess);
            } else {
                httpConnection.setFixedLengthStreamingMode((int) dataSizeGuess);
            }
        }
        return dataSizeGuess;
    }
}
