package org.commcare.network;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by dimagi on 11/07/17.
 */

public class AuthenticationInterceptor implements Interceptor{

    private String credential;

    public AuthenticationInterceptor(String credential) {
        this.credential = credential;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        Request.Builder builder = original.newBuilder()
                .header("Authorization", credential);

        Request request = builder.build();
        return chain.proceed(request);
    }
}
