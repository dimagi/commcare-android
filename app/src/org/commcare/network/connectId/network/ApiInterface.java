package org.commcare.network.connectId.network;

import retrofit2.http.GET;

public interface ApiInterface {
    @GET(ApiEndPoints.ConnectFetchDbKeyURL)
    Call<> getMovies();
}
