package org.commcare.connect.network.connectId;

import org.commcare.dalvik.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


///Todo retry part of the api fails

public class ApiClient {
    public static final String BASE_URL = "https://connectid.dimagi.com";  // Replace with actual base URL
    private static final String API_VERSION = "1.0";  // Replace with actual version value
    private static volatile ApiService apiService;

    private ApiClient() {
    }

    public static ApiService getClientApi() {
        if (apiService == null) {
            synchronized (ApiClient.class) { // Double-checked locking
                if (apiService == null) {
                    apiService = buildRetrofitClient().create(ApiService.class);
                }
            }
        }
        return apiService;
    }

    private static Retrofit buildRetrofitClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG ?
                HttpLoggingInterceptor.Level.BODY :
                HttpLoggingInterceptor.Level.NONE);
        logging.redactHeader("Authorization");
        logging.redactHeader("Cookie");

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    Request requestWithHeaders = originalRequest.newBuilder()
                            .header("Accept", "application/json;version=" + API_VERSION)
                            .build();
                    return chain.proceed(requestWithHeaders);
                })
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}