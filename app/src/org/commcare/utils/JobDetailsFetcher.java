package org.commcare.utils;

import android.content.Context;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JobDetailsFetcher {

    private final Context context;

    public interface JobDetailsCallback {
        void onJobDetailsFetched(List<ConnectJobRecord> jobs);
        void onError();
    }

    public JobDetailsFetcher(Context context ) {
        this.context = context;
    }

    public void getJobDetails(JobDetailsCallback callback) {
        ApiConnect.getConnectOpportunities(context, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        JSONArray json = new JSONArray(responseAsString);
                        List<ConnectJobRecord> jobs = new ArrayList<>(json.length());
                        for (int i = 0; i < json.length(); i++) {
                            JSONObject obj = (JSONObject) json.get(i);
                            jobs.add(ConnectJobRecord.fromJson(obj));
                        }
                        ConnectDatabaseHelper.storeJobs(context, jobs, true);
                        callback.onJobDetailsFetched(jobs);
                        return;
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Toast.makeText(context, R.string.connect_job_list_api_failure, Toast.LENGTH_SHORT).show();
                    Logger.exception("Parsing return from Opportunities request", e);
                }
                callback.onError();
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Toast.makeText(context, R.string.connect_job_list_api_failure, Toast.LENGTH_SHORT).show();
                Logger.log("ERROR", String.format(Locale.getDefault(), "Opportunities call failed: %d", responseCode));
                callback.onError();
            }

            @Override
            public void processNetworkFailure() {
                Toast.makeText(context, R.string.recovery_network_unavailable, Toast.LENGTH_SHORT).show();
                Logger.log("ERROR", "Failed (network)");
                callback.onError();
            }

            @Override
            public void processOldApiError() {
                Toast.makeText(context, R.string.connect_job_list_api_failure, Toast.LENGTH_SHORT).show();
                ConnectNetworkHelper.showOutdatedApiError(context);
                callback.onError();
            }
        });
    }
}
