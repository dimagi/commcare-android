package org.commcare.fragments.connect;

import static org.commcare.activities.LoginActivity.DELIVERY_APP;
import static org.commcare.activities.LoginActivity.JOB_DELIVERY;
import static org.commcare.activities.LoginActivity.JOB_LEARNING;
import static org.commcare.activities.LoginActivity.JOB_NEW_OPPORTUNITY;
import static org.commcare.activities.LoginActivity.LEARN_APP;
import static org.commcare.activities.LoginActivity.NEW_APP;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE_NEW;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_LEARNING;
import static org.commcare.connect.ConnectManager.isAppInstalled;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareActivity;
import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.connect.ConnectDatabaseHelper;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.IConnectAppLauncher;
import org.commcare.connect.network.ApiConnect;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.models.connect.ConnectLoginJobListModel;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
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
    private ConstraintLayout connectTile;
    //    private TabLayout tabLayout;
//    private ViewPager2 viewPager;
    private ViewStateAdapter viewStateAdapter;
    private TextView updateText;
    private IConnectAppLauncher launcher;
    ArrayList<ConnectLoginJobListModel> jobList;
    View view;


    public ConnectJobsListsFragment() {
        // Required empty public constructor
    }

    public static ConnectJobsListsFragment newInstance(IConnectAppLauncher appLauncher) {
        ConnectJobsListsFragment fragment = new ConnectJobsListsFragment();
        fragment.launcher = appLauncher;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().setTitle(R.string.connect_title);

        view = inflater.inflate(R.layout.fragment_connect_jobs_list, container, false);

        connectTile = view.findViewById(R.id.connect_alert_tile);

        updateText = view.findViewById(R.id.connect_jobs_last_update);
        updateUpdatedDate(ConnectDatabaseHelper.getLastJobsUpdate(getContext()));

        ImageView refreshButton = view.findViewById(R.id.connect_jobs_refresh);
        refreshButton.setOnClickListener(v -> refreshData());

//        viewPager = view.findViewById(R.id.jobs_view_pager);
//        viewStateAdapter = new ViewStateAdapter(getChildFragmentManager(), getLifecycle(), (appId, isLearning) -> {
        //Launch app and finish this activity
//            ConnectManager.launchApp(getActivity(), isLearning, appId);
//            getActivity().finish();
//        });
//        viewPager.setAdapter(viewStateAdapter);

//        tabLayout = view.findViewById(R.id.connect_jobs_tabs);
//        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_jobs_all));
//        tabLayout.addTab(tabLayout.newTab().setText(R.string.connect_jobs_mine));
//
//        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
//            @Override
//            public void onPageSelected(int position) {
//                TabLayout.Tab tab = tabLayout.getTabAt(position);
//                tabLayout.selectTab(tab);
//
//                FirebaseAnalyticsUtil.reportConnectTabChange(tab.getText().toString());
//            }
//        });
//
//        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
//            @Override
//            public void onTabSelected(TabLayout.Tab tab) {
//                viewPager.setCurrentItem(tab.getPosition());
//            }
//
//            @Override
//            public void onTabUnselected(TabLayout.Tab tab) {
//
//            }
//
//            @Override
//            public void onTabReselected(TabLayout.Tab tab) {
//
//            }
//        });

//        chooseTab();
        refreshUi();
        refreshData();

        return view;
    }

    public void refreshData() {
        ApiConnect.getConnectOpportunities(getContext(), new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                int newJobs = 0;
                //TODO: Sounds like we don't want a try-catch here, better to crash. Verify before changing
                try {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(responseData));
                    if (responseAsString.length() > 0) {
                        //Parse the JSON
                        JSONArray json = new JSONArray(responseAsString);
                        List<ConnectJobRecord> jobs = new ArrayList<>(json.length());
                        for (int i = 0; i < json.length(); i++) {
                            JSONObject obj = (JSONObject) json.get(i);
                            jobs.add(ConnectJobRecord.fromJson(obj));
                        }

                        //Store retrieved jobs
                        newJobs = ConnectDatabaseHelper.storeJobs(getContext(), jobs, true);
                        setJobListData(jobs);
                    }
                } catch (IOException | JSONException | ParseException e) {
                    Logger.exception("Parsing return from Opportunities request", e);
                }

                reportApiCall(true, newJobs);
                refreshUi();
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                setJobListData(ConnectDatabaseHelper.getJobs(getActivity(), -1, null));
                Logger.log("ERROR", String.format(Locale.getDefault(), "Opportunities call failed: %d", responseCode));
                reportApiCall(false, 0);
                refreshUi();
            }

            @Override
            public void processNetworkFailure() {
                setJobListData(ConnectDatabaseHelper.getJobs(getActivity(), -1, null));
                Logger.log("ERROR", "Failed (network)");
                reportApiCall(false, 0);
                refreshUi();
            }

            @Override
            public void processOldApiError() {
                setJobListData(ConnectDatabaseHelper.getJobs(getActivity(), -1, null));
                ConnectNetworkHelper.showOutdatedApiError(getContext());
                reportApiCall(false, 0);
            }
        });

    }

    private void reportApiCall(boolean success, int newJobs) {
        FirebaseAnalyticsUtil.reportCccApiJobs(success, newJobs);
    }

    private void refreshUi() {
        try {
            updateUpdatedDate(new Date());
            updateSecondaryPhoneConfirmationTile();
            viewStateAdapter.refresh();
            chooseTab();
        } catch (Exception e) {
            //Ignore exception, happens if we leave the page before API call finishes
        }
    }

    private void updateSecondaryPhoneConfirmationTile() {
        boolean show = ConnectManager.shouldShowSecondaryPhoneConfirmationTile(getContext());

        ConnectManager.updateSecondaryPhoneConfirmationTile(getContext(), connectTile, show, v -> {
            ConnectManager.beginSecondaryPhoneVerification((CommCareActivity<?>) getActivity(), success -> {
                updateSecondaryPhoneConfirmationTile();
            });
        });
    }

    private void updateUpdatedDate(Date lastUpdate) {
        updateText.setText(getString(R.string.connect_last_update, ConnectManager.formatDateTime(lastUpdate)));
    }

    private void chooseTab() {
        int numAvailable = ConnectDatabaseHelper.getAvailableJobs(CommCareApplication.instance()).size();
        int index = numAvailable > 0 ? 0 : 1;
//        viewPager.setCurrentItem(index);
//        tabLayout.setScrollPosition(index, 0f, true);
    }

    private static class ViewStateAdapter extends FragmentStateAdapter {
        static ConnectJobsAvailableListFragment availableFragment;
        static ConnectJobsMyListFragment myFragment;
        final IConnectAppLauncher launcher;

        public ViewStateAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle, IConnectAppLauncher appLauncher) {
            super(fragmentManager, lifecycle);
            launcher = appLauncher;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) {
                availableFragment = ConnectJobsAvailableListFragment.newInstance(launcher);
                return availableFragment;
            }

            myFragment = ConnectJobsMyListFragment.newInstance(launcher);
            return myFragment;
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        public void refresh() {
            if (availableFragment != null) {
                availableFragment.updateView();
            }

            if (myFragment != null) {
                myFragment.updateView();
            }
        }
    }

    private void initRecyclerView() {
        RecyclerView rvJobList = view.findViewById(R.id.rvJobList);
        JobListConnectHomeAppsAdapter adapter = new JobListConnectHomeAppsAdapter(getContext(), jobList, (job, view, isAvailable) -> {
            Log.e("DEBUG_TESTING", "job: " + job.getTitle() + "--- isAvailable:  " + isAvailable);
            if (isAvailable) {
                launchJobInfo(job, view);
            } else {
                launchActiveAppForJob(job, view);
            }
        });
        rvJobList.setLayoutManager(new LinearLayoutManager(getContext()));
        rvJobList.setNestedScrollingEnabled(true);
        rvJobList.setAdapter(adapter);
    }

    private void setJobListData(List<ConnectJobRecord> jobs) {
        jobList = new ArrayList<>();
        ArrayList<ConnectLoginJobListModel> availableNewJobs = new ArrayList<>();
        ArrayList<ConnectLoginJobListModel> otherJobs = new ArrayList<>();

        for (ConnectJobRecord job : jobs) {

            int jobStatus = job.getStatus();
            boolean isLearnAppInstalled = isAppInstalled(job.getLearnAppInfo().getAppId());
            boolean isDeliverAppInstalled = isAppInstalled(job.getDeliveryAppInfo().getAppId());

            switch (jobStatus) {
                case STATUS_AVAILABLE_NEW, STATUS_AVAILABLE:
                    availableNewJobs.add(storeJobListData(
                            job.getTitle(),
                            String.valueOf(job.getJobId()),
                            "",
                            job.getProjectStartDate(),
                            job.getDescription(),
                            job.getOrganization(),
                            false,
                            true,
                            false,
                            false,
                            processJobRecords(job, JOB_NEW_OPPORTUNITY),
                            job.getLearningPercentComplete(),
                            job.getCompletedLearningModules(),
                            JOB_NEW_OPPORTUNITY,
                            NEW_APP
                    ));
                    break;

                case STATUS_LEARNING:
                    otherJobs.add(storeJobListData(
                            job.getLearnAppInfo().getName(),
                            String.valueOf(job.getJobId()),
                            job.getLearnAppInfo().getAppId(),
                            job.getProjectStartDate(),
                            job.getLearnAppInfo().getDescription(),
                            job.getLearnAppInfo().getOrganization(),
                            isLearnAppInstalled,
                            false,
                            true,
                            false,
                            processJobRecords(job, JOB_LEARNING),
                            job.getLearningPercentComplete(),
                            job.getCompletedLearningModules(),
                            JOB_LEARNING,
                            LEARN_APP
                    ));
                    break;

                case STATUS_DELIVERING:
                    otherJobs.add(storeJobListData(
                            job.getLearnAppInfo().getName(),
                            String.valueOf(job.getJobId()),
                            job.getLearnAppInfo().getAppId(),
                            job.getProjectStartDate(),
                            job.getLearnAppInfo().getDescription(),
                            job.getLearnAppInfo().getOrganization(),
                            isLearnAppInstalled,
                            false,
                            true,
                            false,
                            processJobRecords(job, JOB_LEARNING),
                            job.getLearningPercentComplete(),
                            job.getCompletedLearningModules(),
                            JOB_LEARNING,
                            LEARN_APP
                    ));
                    otherJobs.add(storeJobListData(
                            job.getDeliveryAppInfo().getName(),
                            String.valueOf(job.getJobId()),
                            job.getDeliveryAppInfo().getAppId(),
                            job.getProjectStartDate(),
                            job.getDeliveryAppInfo().getDescription(),
                            job.getDeliveryAppInfo().getOrganization(),
                            isDeliverAppInstalled,
                            false,
                            false,
                            true,
                            processJobRecords(job, JOB_DELIVERY),
                            job.getLearningPercentComplete(),
                            job.getCompletedLearningModules(),
                            JOB_DELIVERY,
                            DELIVERY_APP
                    ));
                    break;

                default:
                    break;
            }
        }

        Collections.sort(otherJobs, (job1, job2) -> job2.getLastAccessed().compareTo(job1.getLastAccessed()));

        jobList.addAll(availableNewJobs);
        jobList.addAll(otherJobs);

        initRecyclerView();
    }

    private ConnectLoginJobListModel storeJobListData(
            String name,
            String id,
            String appId,
            Date date,
            String description,
            String organization,
            boolean isAppInstalled,
            boolean isNew,
            boolean isLeaningApp,
            boolean isDeliveryApp,
            Date lastAssessedDate,
            int learningProgress,
            int deliveryProgress,
            String jobType,
            String appType
    ) {
        return new ConnectLoginJobListModel(
                name,
                id,
                appId,
                date,
                description,
                organization,
                isAppInstalled,
                isNew,
                isLeaningApp,
                isDeliveryApp,
                lastAssessedDate,
                learningProgress,
                deliveryProgress,
                jobType,
                appType
        );
    }

    public Date processJobRecords(ConnectJobRecord job, String jobType) {
        Date lastAssessedDate = new Date();
        try {
            String learnAppId = job.getLearnAppInfo().getAppId();
            String deliverAppId = job.getDeliveryAppInfo().getAppId();
            if (jobType.equalsIgnoreCase(JOB_LEARNING)) {
                ConnectLinkedAppRecord learnRecord = ConnectDatabaseHelper.getAppData(getActivity(), learnAppId, "");
                return learnRecord != null ? learnRecord.getLastAccessed() : lastAssessedDate;

            } else if (jobType.equalsIgnoreCase(JOB_DELIVERY)) {
                ConnectLinkedAppRecord deliverRecord = ConnectDatabaseHelper.getAppData(getActivity(), deliverAppId, "");
                return deliverRecord != null ? deliverRecord.getLastAccessed() : lastAssessedDate;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lastAssessedDate;
    }

    private void launchActiveAppForJob(ConnectJobRecord job, View view) {
        ConnectManager.setActiveJob(job);

        boolean isLearning = job.getStatus() == ConnectJobRecord.STATUS_LEARNING;
        String appId = isLearning ? job.getLearnAppInfo().getAppId() : job.getDeliveryAppInfo().getAppId();

        if (ConnectManager.isAppInstalled(appId)) {
            launcher.launchApp(appId, isLearning);
        } else {
            int textId = isLearning ? R.string.connect_downloading_learn : R.string.connect_downloading_delivery;
            String title = getString(textId);
            Navigation.findNavController(view).navigate(ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectDownloadingFragment(title, isLearning));
        }
    }

    private void launchJobInfo(ConnectJobRecord job, View view) {
        ConnectManager.setActiveJob(job);

        NavDirections directions;
        switch (job.getStatus()) {
            case ConnectJobRecord.STATUS_AVAILABLE,
                 ConnectJobRecord.STATUS_AVAILABLE_NEW -> {
                directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobIntroFragment();
            }
            case ConnectJobRecord.STATUS_LEARNING -> {
                directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobLearningProgressFragment();
            }
            case ConnectJobRecord.STATUS_DELIVERING -> {
                directions = ConnectJobsListsFragmentDirections.actionConnectJobsListFragmentToConnectJobDeliveryProgressFragment();
            }
            default -> {
                throw new RuntimeException(String.format("Unexpected job status: %d", job.getStatus()));
            }
        }

        Navigation.findNavController(view).navigate(directions);
    }
}