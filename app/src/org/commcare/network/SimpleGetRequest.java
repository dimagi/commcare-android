package org.commcare.network;

import org.commcare.logging.AndroidLogger;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;

/**
 * Makes simple redirect-following GET requests that can be authenticated or not
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class SimpleGetRequest {

    protected static InputStream makeRequest(final String username,
                                             final String password,
                                             URL url) throws IOException {
        if (username == null || password == null) {
            // clear any prior set authenticator to make unauthed requests
            Authenticator.setDefault(null);
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
        return makeModernRequest(url);
    }

    private static InputStream makeModernRequest(URL url) throws IOException {
        int responseCode = -1;
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        setupGetConnection(con);
        con.connect();
        try {
            responseCode = con.getResponseCode();

            return followRedirect(con).getInputStream();
        } catch (IOException e) {
            if (e.getMessage().toLowerCase().contains("authentication")
                    || responseCode == 401) {
                //Android http libraries _suuuuuck_, let's try apache.
                return null;
            } else {
                throw e;
            }
        }
    }

    private static HttpURLConnection followRedirect(HttpURLConnection httpConnection)
            throws IOException {
        final URL url = httpConnection.getURL();
        if (httpConnection.getResponseCode() == 301) {
            String redirectString =
                    url.toString() + " to " + httpConnection.getURL().toString();
            Logger.log(AndroidLogger.TYPE_WARNING_NETWORK,
                    "Attempting 1 stage redirect from " + redirectString);
            //only allow one level of redirection here for now.
            URL newUrl = new URL(httpConnection.getHeaderField("Location"));
            httpConnection.disconnect();
            httpConnection = (HttpURLConnection)newUrl.openConnection();
            setupGetConnection(httpConnection);
            httpConnection.connect();

            // Don't allow redirects _from_ https _to_ https unless they are
            // redirecting to the same server.
            if (!HttpRequestGenerator.isValidRedirect(url, httpConnection.getURL())) {
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK,
                        "Invalid redirect from " + redirectString);
                String errorMessage =
                        "Invalid redirect from secure server to insecure server";
                throw new IOException(errorMessage);
            }
        }

        return httpConnection;
    }

    private static void setupGetConnection(HttpURLConnection con) throws IOException {
        con.setConnectTimeout(ModernHttpRequester.CONNECTION_TIMEOUT);
        con.setReadTimeout(ModernHttpRequester.CONNECTION_SO_TIMEOUT);
        con.setRequestMethod("GET");
        con.setDoInput(true);
        con.setInstanceFollowRedirects(true);
    }
}
