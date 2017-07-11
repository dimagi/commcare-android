package org.commcare.network;

import android.support.annotation.Nullable;

import org.commcare.core.network.ModernHttpRequester;
import org.commcare.logging.AndroidLogger;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

/**
 * Provides an instance of CommCareNetworkService.
 * We have declared everything static in this class as we want to use the same objects (OkHttpClient, Retrofit, …) throughout the app
 * to just open one socket connection that handles all the request and responses including caching and many more.
 */

public class CommCareNetworkServiceGenerator {

    private static final String BASE_URL = "https://www.commcarehq.org/";

    private static Retrofit.Builder builder = new Retrofit.Builder().baseUrl(BASE_URL);

    private static Interceptor redirectionInterceptor = new Interceptor() {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = chain.proceed(request);
            if (!isValidRedirect(request.url(), response.request().url())) {
                Logger.log(AndroidLogger.TYPE_WARNING_NETWORK, "Invalid redirect from " + request.url().toString() + " to " + response.request().url().toString());
                throw new IOException("Invalid redirect from secure server to insecure server");
            }
            return response;
        }
    };

    private static HttpLoggingInterceptor logging =
            new HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BASIC);

    private static OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
            .connectTimeout(ModernHttpRequester.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(ModernHttpRequester.CONNECTION_SO_TIMEOUT, TimeUnit.MILLISECONDS)
            .addInterceptor(redirectionInterceptor)
            .addInterceptor(logging)
            .followRedirects(true);

    private static Retrofit retrofit = builder.build();


    public static CommCareNetworkService createCommCareNetworkService(@Nullable final String credential) {
        if (credential != null) {
            AuthenticationInterceptor interceptor =
                    new AuthenticationInterceptor(credential);
            if (!httpClient.interceptors().contains(interceptor)) {
                httpClient.addInterceptor(interceptor);

                builder.client(httpClient.build());
                retrofit = builder.build();
            }
        } else {
            for (Interceptor interceptor : httpClient.interceptors()) {
                if (interceptor instanceof AuthenticationInterceptor) {
                    httpClient.interceptors().remove(interceptor);
                    retrofit = builder.build();
                    break;
                }
            }
        }
        return retrofit.create(CommCareNetworkService.class);
    }

    private static boolean isValidRedirect(HttpUrl url, HttpUrl newUrl) {
        //unless it's https, don't worry about it
        if (!url.scheme().equals("https")) {
            return true;
        }

        // If https, verify that we're on the same server.
        // Not being so means we got redirected from a secure link to a
        // different link, which isn't acceptable for now.
        return url.host().equals(newUrl.host());
    }
}
