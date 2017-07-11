package org.commcare.network;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

/**
 * Created by dimagi on 06/07/17.
 */

public interface CommCareNetworkService {

    @GET
    Call<ResponseBody> makeGetRequest(@Url String url, @NonNull @QueryMap Map<String, String> params,
                                      @NonNull @HeaderMap Map<String, String> headers);

    @Multipart
    @POST(".")
    Call<ResponseBody> makeMultipartPost(@NonNull @QueryMap Map<String, String> params,
                                         @NonNull @HeaderMap Map<String, String> headers,
                                         @NonNull @Body MultipartBody body);
}
