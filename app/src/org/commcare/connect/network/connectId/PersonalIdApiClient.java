package org.commcare.connect.network.connectId;

import org.commcare.connect.network.ApiService;
import org.commcare.connect.network.base.BaseApiClient;


/**
 * Retrofit client for personalId API
 */
public class PersonalIdApiClient {
    public static final String BASE_URL = "https://connectid.dimagi.com";
    private static final String API_VERSION = "1.0";
    private static volatile ApiService apiService;

    private PersonalIdApiClient() {
    }

    public static ApiService getClientApi() {
        if (apiService == null) {
            synchronized (PersonalIdApiClient.class) { // Double-checked locking
                if (apiService == null) {
                    apiService = BaseApiClient.INSTANCE.buildRetrofitClient(BASE_URL, API_VERSION).create(ApiService.class);
                }
            }
        }
        return apiService;
    }


}