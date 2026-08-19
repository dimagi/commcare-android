package org.commcare.connect.network.connectId;

import org.commcare.connect.network.base.BaseApiClient;


/**
 * Retrofit client for personalId API
 */
public class PersonalIdApiClient {
    public static final String BASE_URL = "https://connectid.dimagi.com";
    public static final String API_VERSION = "2.0";
    private static volatile PersonalIdApiService apiService;

    private PersonalIdApiClient() {
    }

    public static PersonalIdApiService getClientApi() {
        if (apiService == null) {
            synchronized (PersonalIdApiClient.class) { // Double-checked locking
                if (apiService == null) {
                    apiService = BaseApiClient.INSTANCE.buildRetrofitClient(BASE_URL, API_VERSION).create(PersonalIdApiService.class);
                }
            }
        }
        return apiService;
    }


}
