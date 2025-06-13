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
import static org.commcare.connect.ConnectManager.isAppInstalled;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.activities.CommCareActivity;
import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.IConnectAppLauncher;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.models.connect.ConnectLoginJobListModel;
import org.commcare.utils.CommCareNavController;
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
import java.util.Locale;

/**
 * Fragment for showing the two job lists (available and mine)
 *
 * @author dviggiano
 */
public class ConnectJobsListsFragment extends Fragment {
    private CardView connectTile;
    private TextView updateText;
    private IConnectAppLauncher launcher;
    ArrayList<ConnectLoginJobListModel> jobList;
    ArrayList<ConnectLoginJobListModel> corruptJobs = new ArrayList<>();
    View view;


    public ConnectJobsListsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        requireActivity().setTitle(R.string.connect_title);

        view = inflater.inflate(R.layout.fragment_connect_jobs_list, container, false);
        connectTile = view.findViewById(R.id.connect_alert_tile);

        updateText = view.findViewById(R.id.connect_jobs_last_update);
        updateText.setVisibility(View.GONE);
        updateUpdatedDate(ConnectJobUtils.getLastJobsUpdate(getContext()));
        ImageView refreshButton = view.findViewById(R.id.connect_jobs_refresh);
        refreshButton.setOnClickListener(v -> refreshData());
        refreshButton.setVisibility(View.GONE);

        launcher = (appId, isLearning) -> {
            ConnectManager.launchApp(getActivity(), isLearning, appId);
        };

        MenuHost host = (MenuHost)requireActivity();
        host.addMenuProvider(new MenuProvider() {
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

        refreshUi();
        refreshData();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
    }

    public void refreshData() {
        corruptJobs.clear();
        ConnectUserRecord user = ConnectManager.getUser(getContext());
        ApiConnect.getConnectOpportunities(getContext(), user, new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                int totalJobs = 0;
                int newJobs = 0;
                //TODO: Sounds like we don't want a try-catch here, better to crash. Verify before changing
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
                            }catch (JSONException  e) {
                                Logger.exception("Parsing return from Opportunities request", e);
                                handleCorruptJob(obj);
                            }
                        }

                        //Store retrieved jobs
                        totalJobs = jobs.size();
                        newJobs =  ConnectJobUtils.storeJobs(getContext(), jobs, true);
                        setJobListData(jobs);
                    }
                } catch (IOException | JSONException e) {
                    Logger.exception("Parsing / database error return from Opportunities request", e);
                    throw new RuntimeException(e);
                }

                reportApiCall(true, totalJobs, newJobs);
                refreshUi();
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse) {
                setJobListData(ConnectJobUtils.getCompositeJobs(getActivity(), -1, null));
                Logger.log("ERROR", String.format(Locale.getDefault(), "Opportunities call failed: %d", responseCode));
                reportApiCall(false, 0, 0);
                refreshUi();
            }

            @Override
            public void processNetworkFailure() {
                setJobListData(ConnectJobUtils.getCompositeJobs(getActivity(), -1, null));
                Logger.log("ERROR", "Failed (network)");
                reportApiCall(false, 0, 0);
                refreshUi();
            }

            @Override
            public void processOldApiError() {
                setJobListData(ConnectJobUtils.getCompositeJobs(getActivity(), -1, null));
                ConnectNetworkHelper.showOutdatedApiError(getContext());
                reportApiCall(false, 0, 0);
                refreshUi();
            }

            @Override
            public void processTokenUnavailableError() {
                setJobListData(ConnectJobUtils.getCompositeJobs(getActivity(), -1, null));
                ConnectNetworkHelper.handleTokenUnavailableException(getContext());
                reportApiCall(false, 0, 0);
                refreshUi();
            }

            @Override
            public void processTokenRequestDeniedError() {
                setJobListData(ConnectJobUtils.getCompositeJobs(getActivity(), -1, null));
                ConnectNetworkHelper.handleTokenDeniedException();
                reportApiCall(false, 0, 0);
                refreshUi();
            }
        });

    }

    private void reportApiCall(boolean success, int totalJobs, int newJobs) {
        FirebaseAnalyticsUtil.reportCccApiJobs(success, totalJobs, newJobs);
    }

    private void refreshUi() {
        //Make sure we still have context
        Context context = getContext();
        if(context != null) {
            updateUpdatedDate(new Date());
            updateSecondaryPhoneConfirmationTile(context);
        }
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

    private void updateSecondaryPhoneConfirmationTile(Context context) {
        boolean show = PersonalIdManager.getInstance().shouldShowSecondaryPhoneConfirmationTile(context);

        ConnectManager.updateSecondaryPhoneConfirmationTile(context, connectTile, show, v -> {
            ConnectManager.beginSecondaryPhoneVerification((CommCareActivity<?>) getActivity(), success -> {
                updateSecondaryPhoneConfirmationTile(context);
            });
        });
    }

    private void updateUpdatedDate(Date lastUpdate) {
        updateText.setText(getString(R.string.connect_last_update, ConnectManager.formatDateTime(lastUpdate)));
    }

    private void initRecyclerView() {
        RecyclerView rvJobList = view.findViewById(R.id.rvJobList);

        TextView noJobsText = view.findViewById(R.id.connect_no_jobs_text);
        noJobsText.setVisibility(corruptJobs.isEmpty() && jobList.isEmpty() ? View.VISIBLE : View.GONE);

        JobListConnectHomeAppsAdapter adapter = new JobListConnectHomeAppsAdapter(getContext(), jobList,
                corruptJobs, (job, isLearning, appId, jobType) -> {
            if (jobType.equals(JOB_NEW_OPPORTUNITY)) {
                launchJobInfo(job);
            } else {
                launchAppForJob(job, isLearning);
            }
        });

        rvJobList.setLayoutManager(new LinearLayoutManager(getContext()));
        rvJobList.setNestedScrollingEnabled(true);
        rvJobList.setAdapter(adapter);
    }

    private void launchJobInfo(ConnectJobRecord job) {
        ConnectManager.setActiveJob(job);
        CommCareNavController.navigateSafely(Navigation.findNavController(view),
                ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobIntroFragment());
    }

    private void launchAppForJob(ConnectJobRecord job, boolean isLearning) {
        ConnectManager.setActiveJob(job);

        String appId = isLearning ? job.getLearnAppInfo().getAppId() : job.getDeliveryAppInfo().getAppId();

        if (ConnectManager.isAppInstalled(appId)) {
            launcher.launchApp(appId, isLearning);
        } else {
            int textId = isLearning ? R.string.connect_downloading_learn : R.string.connect_downloading_delivery;
            String title = getString(textId);
            CommCareNavController.navigateSafely(Navigation.findNavController(view),
                    ConnectJobsListsFragmentDirections
                            .actionConnectJobsListFragmentToConnectDownloadingFragment(title, isLearning));
        }
    }

    private void setJobListData(List<ConnectJobRecord> jobs) {
        jobList = new ArrayList<>();
        ArrayList<ConnectLoginJobListModel> availableNewJobs = new ArrayList<>();
        ArrayList<ConnectLoginJobListModel> learnApps = new ArrayList<>();
        ArrayList<ConnectLoginJobListModel> deliverApps = new ArrayList<>();
        ArrayList<ConnectLoginJobListModel> reviewLearnApps = new ArrayList<>();
        ArrayList<ConnectLoginJobListModel> finishedItems = new ArrayList<>();

        for (ConnectJobRecord job : jobs) {
            int jobStatus = job.getStatus();
            boolean finished = job.isFinished();
            boolean isLearnAppInstalled = isAppInstalled(job.getLearnAppInfo().getAppId());
            boolean isDeliverAppInstalled = isAppInstalled(job.getDeliveryAppInfo().getAppId());

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

                    if(finished) {
                        finishedItems.add(model);
                    } else {
                        learnApps.add(model);
                    }

                    break;

                case STATUS_DELIVERING:
                    ConnectLoginJobListModel learnModel = createJobModel(job, JOB_LEARNING, LEARN_APP,
                            isLearnAppInstalled, false, true, false);

                    ConnectLoginJobListModel deliverModel = createJobModel(job, JOB_DELIVERY, DELIVERY_APP,
                            isDeliverAppInstalled, false, false, true);

                    reviewLearnApps.add(learnModel);

                    if(finished) {
                        finishedItems.add(deliverModel);
                    } else {
                        deliverApps.add(deliverModel);
                    }

                    break;
            }
        }

        Collections.sort(learnApps, (job1, job2) -> job1.getLastAccessed().compareTo(job2.getLastAccessed()));
        Collections.sort(deliverApps, (job1, job2) -> job1.getLastAccessed().compareTo(job2.getLastAccessed()));
        Collections.sort(reviewLearnApps, (job1, job2) -> job1.getLastAccessed().compareTo(job2.getLastAccessed()));
        Collections.sort(finishedItems, (job1, job2) -> job1.getLastAccessed().compareTo(job2.getLastAccessed()));
        jobList.addAll(availableNewJobs);
        jobList.addAll(learnApps);
        jobList.addAll(deliverApps);
        jobList.addAll(reviewLearnApps);
        jobList.addAll(finishedItems);
        initRecyclerView();
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
                getLastAccessedDate(job, jobType),
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

    private String getAppIdForType(ConnectJobRecord job, String jobType) {
        return jobType.equalsIgnoreCase(JOB_LEARNING)
                ? job.getLearnAppInfo().getAppId()
                : job.getDeliveryAppInfo().getAppId();
    }

    private String getDescriptionForType(ConnectJobRecord job, String jobType) {
        return jobType.equalsIgnoreCase(JOB_LEARNING)
                ? job.getLearnAppInfo().getDescription()
                : job.getDeliveryAppInfo().getDescription();
    }

    private String getOrganizationForType(ConnectJobRecord job, String jobType) {
        return jobType.equalsIgnoreCase(JOB_LEARNING)
                ? job.getLearnAppInfo().getOrganization()
                : job.getDeliveryAppInfo().getOrganization();
    }

    public Date getLastAccessedDate(ConnectJobRecord job, String jobType) {
        ConnectAppRecord appRecord = null;
        if (jobType.equalsIgnoreCase(JOB_LEARNING)) {
            appRecord = job.getLearnAppInfo();
        } else if (jobType.equalsIgnoreCase(JOB_DELIVERY)) {
            appRecord = job.getDeliveryAppInfo();
        }

        if(appRecord != null) {
            ConnectLinkedAppRecord linkedAppRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(
                    getActivity(), appRecord.getAppId(), "");
            if (linkedAppRecord != null) {
                return linkedAppRecord.getLastAccessed();
            }
        }

        return new Date();
    }
}
