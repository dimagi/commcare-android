package org.commcare.fragments.connect;

import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE_NEW;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_LEARNING;
import static org.commcare.connect.ConnectConstants.DELIVERY_APP;
import static org.commcare.connect.ConnectConstants.JOB_DELIVERY;
import static org.commcare.connect.ConnectConstants.JOB_LEARNING;
import static org.commcare.connect.ConnectConstants.JOB_NEW_OPPORTUNITY;
import static org.commcare.connect.ConnectConstants.LEARN_APP;
import static org.commcare.connect.ConnectConstants.NEW_APP;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.type.DateTime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectAppUtils;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.IConnectAppLauncher;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectJobsListBinding;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.models.connect.ConnectLoginJobListModel;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Fragment for showing the two job lists (available and mine)
 *
 * @author dviggiano
 */
public class ConnectJobsListsFragment extends Fragment {
    private FragmentConnectJobsListBinding binding;
    private IConnectAppLauncher launcher;
    ArrayList<ConnectLoginJobListModel> jobList;
    ArrayList<ConnectLoginJobListModel> corruptJobs = new ArrayList<>();

    private ConnectUserRecord userRecord;

    public ConnectJobsListsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        requireActivity().setTitle(R.string.connect_title);

        binding = FragmentConnectJobsListBinding.inflate(inflater, container, false);
        userRecord= ConnectUserDatabaseUtil.getUser(requireContext());
        launcher = (appId, isLearning) -> {
            ConnectAppUtils.INSTANCE.launchApp(getActivity(), isLearning, appId);
        };

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                //Activity loads the menu
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_sync) {
                    refreshData();
                    return true;
                }

                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        refreshData();

        return binding.getRoot();
    }

    public void refreshData() {
        corruptJobs.clear();
        ApiConnect.getConnectOpportunities(getContext(), userRecord, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                int totalJobs = 0;
                int newJobs = 0;
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (!responseAsString.isEmpty()) {
                        //Parse the JSON
                        JSONArray json = new JSONArray(responseAsString);
                        List<ConnectJobRecord> jobs = new ArrayList<>(json.length());
                        for (int i = 0; i < json.length(); i++) {
                            JSONObject obj=null;
                            try {
                                obj = (JSONObject)json.get(i);
                                jobs.add(ConnectJobRecord.fromJson(obj));
                            } catch (JSONException  e) {
                                Logger.exception("Parsing return from Opportunities request", e);
                                handleCorruptJob(obj);
                            }
                        }

                        //Store retrieved jobs
                        totalJobs = jobs.size();
                        newJobs =  ConnectJobUtils.storeJobs(getContext(), jobs, true);
                        setJobListData(jobs);
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    Logger.exception("Error parsing return from Opportunities request", e);
                }

                reportApiCall(true, totalJobs, newJobs);
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse, String url) {
                navigateFailure();
            }

            @Override
            public void processNetworkFailure() {
                navigateFailure();
            }

            @Override
            public void processOldApiError() {
                navigateFailure();
                ConnectNetworkHelper.showOutdatedApiError(getContext());
            }

            @Override
            public void processTokenUnavailableError() {
                navigateFailure();
                ConnectNetworkHelper.handleTokenUnavailableException(getContext());
            }

            @Override
            public void processTokenRequestDeniedError() {
                navigateFailure();
                ConnectNetworkHelper.handleTokenDeniedException();
            }
        });
    }

    private void navigateFailure() {
        reportApiCall(false, 0, 0);
        setJobListData(ConnectJobUtils.getCompositeJobs(getActivity(), -1, null));
    }

    private void reportApiCall(boolean success, int totalJobs, int newJobs) {
        FirebaseAnalyticsUtil.reportCccApiJobs(success, totalJobs, newJobs);
    }

    private void handleCorruptJob(JSONObject obj) {
        if(obj!=null) {
            try {
                corruptJobs.add(createJobModel(ConnectJobRecord.corruptJobfromJson(obj)));
            } catch (JSONException e) {
                Logger.exception("JSONException while retrieving corrupt opportunity title", e);
            }
        }
    }

    private void initRecyclerView() {
        binding.connectNoJobsText.setVisibility(corruptJobs.isEmpty() && jobList.isEmpty() ?
                View.VISIBLE : View.GONE);

        JobListConnectHomeAppsAdapter adapter = new JobListConnectHomeAppsAdapter(getContext(), jobList,
                corruptJobs, (job, isLearning, appId, jobType) -> {
            if (jobType.equals(JOB_NEW_OPPORTUNITY)) {
                launchJobInfo(job);
            } else {
                launchAppForJob(job, isLearning);
            }
        });

        binding.rvJobList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvJobList.setNestedScrollingEnabled(true);
        binding.rvJobList.setAdapter(adapter);
    }

    private void launchJobInfo(ConnectJobRecord job) {
        ConnectJobHelper.INSTANCE.setActiveJob(job);
        Navigation.findNavController(binding.getRoot()).navigate(ConnectJobsListsFragmentDirections
                .actionConnectJobsListFragmentToConnectJobIntroFragment());
    }

    private void launchAppForJob(ConnectJobRecord job, boolean isLearning) {
        ConnectJobHelper.INSTANCE.setActiveJob(job);

        String appId = isLearning ? job.getLearnAppInfo().getAppId() : job.getDeliveryAppInfo().getAppId();

        if (ConnectAppUtils.INSTANCE.isAppInstalled(appId)) {
            launcher.launchApp(appId, isLearning);
        } else {
            int textId = isLearning ? R.string.connect_downloading_learn : R.string.connect_downloading_delivery;
            Navigation.findNavController(binding.getRoot()).navigate(ConnectJobsListsFragmentDirections
                            .actionConnectJobsListFragmentToConnectDownloadingFragment(
                                    getString(textId), isLearning));
        }
    }

    private void setJobListData(List<ConnectJobRecord> jobs) {
        if(getActivity()!=null) {
            jobList = new ArrayList<>();
            ArrayList<ConnectLoginJobListModel> availableNewJobs = new ArrayList<>();
            ArrayList<ConnectLoginJobListModel> learnApps = new ArrayList<>();
            ArrayList<ConnectLoginJobListModel> deliverApps = new ArrayList<>();
            ArrayList<ConnectLoginJobListModel> reviewLearnApps = new ArrayList<>();
            ArrayList<ConnectLoginJobListModel> finishedItems = new ArrayList<>();

            for (ConnectJobRecord job : jobs) {
                int jobStatus = job.getStatus();
                boolean finished = job.isFinished();
                boolean isLearnAppInstalled = ConnectAppUtils.INSTANCE.isAppInstalled(
                        job.getLearnAppInfo().getAppId());
                boolean isDeliverAppInstalled = ConnectAppUtils.INSTANCE.isAppInstalled(
                        job.getDeliveryAppInfo().getAppId());

                switch (jobStatus) {
                    case STATUS_AVAILABLE_NEW, STATUS_AVAILABLE:
                        if (!finished) {
                            availableNewJobs.add(createJobModel(job, JOB_NEW_OPPORTUNITY, NEW_APP,
                                    true, true, false, false));
                        }
                        break;

                    case STATUS_LEARNING:
                        ConnectLoginJobListModel model = createJobModel(job, JOB_LEARNING, LEARN_APP,
                                isLearnAppInstalled, false, true, false);

                        ArrayList<ConnectLoginJobListModel> learnList = finished ? finishedItems : learnApps;
                        learnList.add(model);

                        break;

                    case STATUS_DELIVERING:
                        ConnectLoginJobListModel learnModel = createJobModel(job, JOB_LEARNING, LEARN_APP,
                                isLearnAppInstalled, false, true, false);

                        ConnectLoginJobListModel deliverModel = createJobModel(job, JOB_DELIVERY, DELIVERY_APP,
                                isDeliverAppInstalled, false, false, true);

                        reviewLearnApps.add(learnModel);

                        ArrayList<ConnectLoginJobListModel> deliverList = finished ? finishedItems : deliverApps;
                        deliverList.add(deliverModel);

                        break;
                }
            }

            Collections.sort(learnApps, (job1, job2) -> job1.getLastAccessed().compareTo(job2.getLastAccessed()));
            Collections.sort(deliverApps,
                    (job1, job2) -> job1.getLastAccessed().compareTo(job2.getLastAccessed()));
            Collections.sort(reviewLearnApps,
                    (job1, job2) -> job1.getLastAccessed().compareTo(job2.getLastAccessed()));
            Collections.sort(finishedItems,
                    (job1, job2) -> job1.getLastAccessed().compareTo(job2.getLastAccessed()));
            jobList.addAll(availableNewJobs);
            jobList.addAll(learnApps);
            jobList.addAll(deliverApps);
            jobList.addAll(reviewLearnApps);
            jobList.addAll(finishedItems);
            initRecyclerView();
        }
    }

    private ConnectLoginJobListModel createJobModel(
            ConnectJobRecord job,
            String jobType,
            String appType,
            boolean isAppInstalled,
            boolean isNew,
            boolean isLearningApp,
            boolean isDeliveryApp
    ) {
        return new ConnectLoginJobListModel(
                job.getTitle(),
                String.valueOf(job.getJobId()),
                getAppIdForType(job, jobType),
                job.getProjectEndDate(),
                getDescriptionForType(job, jobType),
                getOrganizationForType(job, jobType),
                isAppInstalled,
                isNew,
                isLearningApp,
                isDeliveryApp,
                processJobRecords(job, jobType),
                job.getLearningPercentComplete(),
                job.getCompletedLearningModules(),
                jobType,
                appType,
                job
        );
    }

    private ConnectLoginJobListModel createJobModel(ConnectJobRecord job) {
        return new ConnectLoginJobListModel(job.getTitle(), job);
    }

    private ConnectAppRecord getAppRecord(ConnectJobRecord job, String jobType) {
        return jobType.equalsIgnoreCase(JOB_LEARNING) ?
                job.getLearnAppInfo() :
                job.getDeliveryAppInfo();
    }

    private String getAppIdForType(ConnectJobRecord job, String jobType) {
        return getAppRecord(job, jobType).getAppId();
    }

    private String getDescriptionForType(ConnectJobRecord job, String jobType) {
        return getAppRecord(job, jobType).getDescription();
    }

    private String getOrganizationForType(ConnectJobRecord job, String jobType) {
        return getAppRecord(job, jobType).getOrganization();
    }

    public Date processJobRecords(ConnectJobRecord job, String jobType) {
        String appId = getAppRecord(job, jobType).getAppId();

            ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(
                    requireActivity(), appId, userRecord.getUserId());

        return appRecord != null ? appRecord.getLastAccessed() : new Date();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
