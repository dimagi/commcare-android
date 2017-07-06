package org.commcare.network;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

/**
 * Created by dimagi on 06/07/17.
 */

public interface CommCareNetworkService {

    @GET(".")
    Call<ResponseBody> makeCaseFetchRequest(@QueryMap Map<String, String> params, @HeaderMap Map<String, String> headers);
}
