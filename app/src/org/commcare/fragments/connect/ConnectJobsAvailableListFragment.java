package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.activities.connect.ConnectIdDatabaseHelper;
import org.commcare.activities.connect.ConnectIdNetworkHelper;
import org.commcare.adapters.ConnectJobAdapter;
import org.commcare.android.database.connect.models.ConnectJob;
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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Fragment for showing the available jobs list
 *
 * @author dviggiano
 */
public class ConnectJobsAvailableListFragment extends Fragment {
    public ConnectJobsAvailableListFragment() {
        // Required empty public constructor
    }

    public static ConnectJobsAvailableListFragment newInstance() {
        return new ConnectJobsAvailableListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect_available_jobs_list, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.available_jobs_list);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        ConnectIdNetworkHelper.getConnectOpportunities(getContext(), new ConnectIdNetworkHelper.INetworkResultHandler() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONArray json = new JSONArray(responseAsString);
                        List<ConnectJob> jobs = new ArrayList<>(json.length());
                        for(int i=0; i<json.length(); i++) {
                            JSONObject obj = (JSONObject)json.get(i);
                            jobs.add(ConnectJob.fromJson(obj));
                        }

                        //Store retrieved jobs
                        ConnectIdDatabaseHelper.storeJobs(getContext(), jobs);

                        recyclerView.setAdapter(new ConnectJobAdapter(true));
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from Opportunities request", e);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Logger.log("ERROR", String.format(Locale.getDefault(), "Failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                Logger.log("ERROR", "Failed (network)");
            }
        });

        return view;
    }
}
