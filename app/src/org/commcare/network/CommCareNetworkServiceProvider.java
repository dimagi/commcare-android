package org.commcare.network;

import android.content.Context;

import org.commcare.preferences.CommCareServerPreferences;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

/**
 * Created by dimagi on 06/07/17.
 */

public class CommCareNetworkServiceProvider {

    public static CommCareNetworkService provideCommCareNetworkService(OkHttpClient okHttpClient) {
        return new Retrofit.Builder()
                .baseUrl(CommCareServerPreferences.getDataServerKey())
                .client(okHttpClient)
                .build()
                .create(CommCareNetworkService.class);
    }
}
