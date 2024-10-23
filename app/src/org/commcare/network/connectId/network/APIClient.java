package org.commcare.network.connectId.network;

import retrofit2.Retrofit;

public class APIClient {
    public static String BASE_URL = "YOUR_BASE_URL";
    private static Retrofit retrofit;
    public static Retrofit getClient(){
        if(retrofit == null){
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .build();
        }
        return retrofit;
    }
}
