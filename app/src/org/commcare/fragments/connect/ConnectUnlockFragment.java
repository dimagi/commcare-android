package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.common.base.Strings;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
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

import javax.annotation.Nullable;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;

public class ConnectUnlockFragment extends Fragment {
    View view;
    String redirectionAction = "";
    String opportunityId = "";

    public ConnectUnlockFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().setTitle(R.string.connect_title);

        redirectionAction = getArguments().getString("action");
        opportunityId = getArguments().getString("opportunity_id");

        view = inflater.inflate(R.layout.blank_activity, container, false);
        view.setBackgroundColor(getResources().getColor(R.color.white));

        ConnectManager.unlockConnect((CommCareActivity<?>)requireActivity(), success -> {
            if (success) {
                retrieveOpportunities();
            } else {
                requireActivity().finish();
            }
        });

        return view;
    }

    public void retrieveOpportunities() {
        ApiConnect.getConnectOpportunities(requireContext(), new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONArray json = new JSONArray(responseAsString);
                        List<ConnectJobRecord> jobs = new ArrayList<>(json.length());
                        for (int i = 0; i < json.length(); i++) {
                            try {
                                JSONObject obj = (JSONObject) json.get(i);
                                ConnectJobRecord job = ConnectJobRecord.fromJson(obj);
                                jobs.add(job);
                            }catch (JSONException  e) {
                                Logger.exception("Parsing return from Opportunities request", e);
                            }
                        }
                        ConnectDatabaseHelper.storeJobs(requireContext(), jobs, true);
                    }
                } catch (IOException | JSONException e) {
                    Toast.makeText(requireContext(), R.string.connect_job_list_api_failure, Toast.LENGTH_SHORT).show();
                    Logger.exception("Parsing return from Opportunities request", e);
                    throw new RuntimeException(e);
                }

                setFragmentRedirection();
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                setFragmentRedirection();
                Toast.makeText(requireContext(), R.string.connect_job_list_api_failure, Toast.LENGTH_SHORT).show();
                Logger.log("ERROR", String.format(Locale.getDefault(), "Opportunities call failed: %d", responseCode));
            }

            @Override
            public void processNetworkFailure() {
                setFragmentRedirection();
                Toast.makeText(requireContext(), R.string.recovery_network_unavailable, Toast.LENGTH_SHORT).show();
                Logger.log("ERROR", "Failed (network)");
            }

            @Override
            public void processOldApiError() {
                setFragmentRedirection();
                Toast.makeText(requireContext(), R.string.connect_job_list_api_failure, Toast.LENGTH_SHORT).show();
                ConnectNetworkHelper.showOutdatedApiError(requireContext());
            }
        });
    }

    /**
     * Sets the fragment redirection based on the redirection action.
     * <p>
     * This method determines the fragment to be displayed using the getFragmentId() method,
     * prepares a bundle with additional data, and navigates to the appropriate fragment.
     */
    private void setFragmentRedirection() {
        Logger.log("ConnectUnlockFragment", "Redirecting after unlock fragment");
        boolean buttons = getArguments().getBoolean("buttons", true);
        Bundle bundle = new Bundle();
        bundle.putBoolean("showLaunch", buttons);

        if(!Strings.isNullOrEmpty(opportunityId)) {
            int jobId = Integer.parseInt(opportunityId);
            ConnectJobRecord job = ConnectDatabaseHelper.getJob(requireContext(), jobId);
            if(job != null) {
                ConnectManager.setActiveJob(job);
            }
        }

        int fragmentId;
        if (redirectionAction.equals(ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE)) {
            fragmentId = R.id.connect_job_intro_fragment;
        } else if (redirectionAction.equals(ConnectConstants.CCC_DEST_LEARN_PROGRESS)) {
            fragmentId = R.id.connect_job_learning_progress_fragment;
        } else if (redirectionAction.equals(ConnectConstants.CCC_DEST_DELIVERY_PROGRESS)) {
            fragmentId = R.id.connect_job_delivery_progress_fragment;
            // Set the tab position in the bundle based on the redirection action
            bundle.putString("tabPosition", "0");
        } else if (redirectionAction.equals(ConnectConstants.CCC_DEST_PAYMENTS)) {
            fragmentId = R.id.connect_job_delivery_progress_fragment;
            // Set the tab position in the bundle based on the redirection action
            bundle.putString("tabPosition", "1");
        } else {
            //Default case
            fragmentId = R.id.connect_jobs_list_fragment;
        }

        NavController navController = Navigation.findNavController(view);
        navController.popBackStack();
        NavOptions options = new NavOptions.Builder()
                .setPopUpTo(navController.getGraph().getStartDestinationId(), true, true)
                .build();
        navController.navigate(fragmentId, bundle, options);
    }
}
