package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectAppUtils;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.repository.ConnectRepository;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.viewmodel.ConnectJobsListViewModel;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectJobsListBinding;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.fragments.base.BaseConnectFragment;
import org.commcare.interfaces.OnJobSelectionClick;
import org.commcare.models.connect.ConnectLoginJobListModel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_AVAILABLE_NEW;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_DELIVERING;
import static org.commcare.android.database.connect.models.ConnectJobRecord.STATUS_LEARNING;
import static org.commcare.connect.ConnectConstants.DELIVERY_APP;
import static org.commcare.connect.ConnectConstants.LEARN_APP;
import static org.commcare.connect.ConnectConstants.NEW_APP;

/**
 * Fragment for showing the two job lists (available and mine)
 *
 * @author dviggiano
 */
public class ConnectJobsListsFragment extends BaseConnectFragment<FragmentConnectJobsListBinding>
        implements RefreshableFragment {

    ArrayList<ConnectLoginJobListModel> inProgressJobs;
    ArrayList<ConnectLoginJobListModel> newJobs;
    ArrayList<ConnectLoginJobListModel> finishedJobs;
    ArrayList<ConnectLoginJobListModel> corruptJobs = new ArrayList<>();

    private ConnectJobsListViewModel viewModel;

    public ConnectJobsListsFragment() {
        // Required empty public constructor
    }

    @Override
    public @NotNull View onCreateView(
            @NotNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        requireActivity().setTitle(R.string.connect_title);
        ((ConnectActivity)requireActivity()).setWaitDialogEnabled(false);
        viewModel = new ViewModelProvider(this).get(ConnectJobsListViewModel.class);
        observeOpportunities();
        refresh(false);
        return view;
    }

    @Override
    public void refresh(boolean forceRefresh) {
        viewModel.loadOpportunities(forceRefresh);
    }

    private void observeOpportunities() {
        observeDataState(
                viewModel.getOpportunities(),
                cached -> {
                    corruptJobs.clear();
                    setJobListData(cached);
                },
                success -> {
                    corruptJobs.clear();
                    setJobListData(success);
                }
        );
    }

    private void initRecyclerView() {
        boolean noJobsAvailable = corruptJobs.isEmpty() && inProgressJobs.isEmpty()
                && newJobs.isEmpty() && finishedJobs.isEmpty();
        getBinding().connectNoJobsText.setVisibility(noJobsAvailable ? View.VISIBLE : View.GONE);

        JobListConnectHomeAppsAdapter adapter = new JobListConnectHomeAppsAdapter(
                getContext(),
                inProgressJobs,
                newJobs,
                finishedJobs,
                corruptJobs,
                (job, isLearning, appId, jobType, action) -> {
                    if (action == OnJobSelectionClick.Action.VIEW_INFO) {
                        setActiveJob(job);
                        if (job.getStatus() == STATUS_AVAILABLE_NEW
                                || job.getStatus() == STATUS_AVAILABLE) {
                            navigateToJobIntro();
                        } else {
                            navigateToJobDetailBottomSheet(getView());
                        }
                        return;
                    }

                    if (jobType == ConnectLoginJobListModel.JobListEntryType.NEW_OPPORTUNITY) {
                        launchJobInfo(job);
                    } else {
                        launchAppForJob(job, isLearning);
                    }
                }
        );

        getBinding().rvJobList.setLayoutManager(new LinearLayoutManager(getContext()));
        getBinding().rvJobList.setNestedScrollingEnabled(true);
        getBinding().rvJobList.setAdapter(adapter);
    }

    private void navigateToJobDetailBottomSheet(View view) {
        NavController navController = Navigation.findNavController(view);
        if (navController.getCurrentDestination() != null &&
                navController.getCurrentDestination().getId()
                        == R.id.connect_job_detail_bottom_sheet_dialog_fragment) {
            return;
        }
        navController.navigate(R.id.connect_job_detail_bottom_sheet_dialog_fragment);
    }

    private void navigateToJobIntro() {
        Navigation.findNavController(getBinding().getRoot()).navigate(
                ConnectJobsListsFragmentDirections
                        .actionConnectJobsListFragmentToConnectJobIntroFragment()
        );
    }

    private void launchJobInfo(ConnectJobRecord job) {
        setActiveJob(job);
        Navigation.findNavController(getBinding().getRoot()).navigate(
                ConnectJobsListsFragmentDirections
                        .actionConnectJobsListFragmentToConnectJobIntroFragment()
        );
    }

    private void launchAppForJob(ConnectJobRecord job, boolean isLearning) {
        setActiveJob(job);

        String appId = isLearning
                ? job.getLearnAppInfo().getAppId()
                : job.getDeliveryAppInfo().getAppId();

        if (job.deliveryComplete()) {
            navigateToDeliveryProgress();
        } else if (!job.passedAssessment()) {
            navigateToLearnProgress();
        } else if (isLearning && job.passedAssessment()) {
            navigateToDeliveryDetails();
        } else if (AppUtils.isAppInstalled(appId)) {
            ConnectAppUtils.INSTANCE.launchApp(requireActivity(), isLearning, appId);
        } else {
            int textId = isLearning
                    ? R.string.connect_downloading_learn
                    : R.string.connect_downloading_delivery;
            Navigation.findNavController(getBinding().getRoot()).navigate(
                    ConnectJobsListsFragmentDirections
                            .actionConnectJobsListFragmentToConnectDownloadingFragment(
                                    getString(textId),
                                    isLearning
                            )
            );
        }
    }

    private void navigateToDeliveryProgress() {
        Navigation.findNavController(getBinding().getRoot())
                .navigate(
                        ConnectJobsListsFragmentDirections
                                .actionConnectJobsListFragmentToConnectJobDeliveryProgressFragment()
                );
    }

    private void navigateToLearnProgress() {
        Navigation.findNavController(getBinding().getRoot())
                .navigate(
                        ConnectJobsListsFragmentDirections
                                .actionConnectJobsListFragmentToConnectJobLearningProgressFragment()
                );
    }

    private void navigateToDeliveryDetails() {
        Navigation.findNavController(getBinding().getRoot())
                .navigate(
                        ConnectJobsListsFragmentDirections
                                .actionConnectJobsListFragmentToConnectJobDeliveryDetailsFragment(
                                        true
                                )
                );
    }

    private void setActiveJob(ConnectJobRecord job) {
        CommCareApplication.instance().setConnectJobIdForAnalytics(job);
        ((ConnectActivity) requireActivity()).setActiveJob(job);
    }

    private void setJobListData(List<ConnectJobRecord> jobs) {
        inProgressJobs = new ArrayList<>();
        List<ConnectLoginJobListModel> inProgressCompleteJobs = new ArrayList<>();
        newJobs = new ArrayList<>();
        finishedJobs = new ArrayList<>();
        for (ConnectJobRecord job : jobs) {
            boolean isLearnAppInstalled =
                    AppUtils.isAppInstalled(job.getLearnAppInfo().getAppId());
            boolean isDeliverAppInstalled =
                    AppUtils.isAppInstalled(job.getDeliveryAppInfo().getAppId());
            ConnectJobRecord compositeJob =
                    ConnectJobUtils.getCompositeJob(requireActivity(), job.getJobUUID());
            Objects.requireNonNull(compositeJob);
            boolean userCompletedDelivery =
                    compositeJob.getStatus() == STATUS_DELIVERING &&
                            compositeJob.getDeliveryProgressPercentage() == 100;
            if (compositeJob.isFinished()) {
                finishedJobs.add(createJobModel(
                        compositeJob,
                        ConnectLoginJobListModel.JobListEntryType.DELIVERY,
                        DELIVERY_APP,
                        isDeliverAppInstalled,
                        false,
                        false,
                        true,
                        true,
                        userCompletedDelivery
                ));
                continue;
            }
            switch (job.getStatus()) {
                case STATUS_AVAILABLE_NEW, STATUS_AVAILABLE:
                    newJobs.add(
                            createJobModel(
                                    compositeJob,
                                    ConnectLoginJobListModel.JobListEntryType.NEW_OPPORTUNITY,
                                    NEW_APP,
                                    true,
                                    true,
                                    false,
                                    false,
                                    false,
                                    false
                            )
                    );
                    break;

                case STATUS_LEARNING:
                    inProgressJobs.add(
                            createJobModel(
                                    compositeJob,
                                    ConnectLoginJobListModel.JobListEntryType.LEARNING,
                                    LEARN_APP,
                                    isLearnAppInstalled,
                                    false,
                                    true,
                                    false,
                                    false,
                                    false
                            )
                    );
                    break;

                case STATUS_DELIVERING: {
                    ConnectLoginJobListModel model = createJobModel(
                            compositeJob,
                            ConnectLoginJobListModel.JobListEntryType.DELIVERY,
                            DELIVERY_APP,
                            isDeliverAppInstalled,
                            false,
                            false,
                            true,
                            false,
                            userCompletedDelivery
                    );

                    if (userCompletedDelivery) {
                        inProgressCompleteJobs.add(model);
                    } else {
                        inProgressJobs.add(model);
                    }
                    break;
                }
            }
        }

        sortJobListByLastAccessed(inProgressJobs);
        sortJobListByLastAccessed(inProgressCompleteJobs);

        //Jobs with completed delivery moved to the end
        inProgressJobs.addAll(inProgressCompleteJobs);

        sortJobListByLastAccessed(newJobs);
        sortJobListByLastAccessed(finishedJobs);
        initRecyclerView();
    }

    private void sortJobListByLastAccessed(List<ConnectLoginJobListModel> list) {
        Collections.sort(
                list,
                (job1, job2) ->
                        job1.getLastAccessed().compareTo(job2.getLastAccessed())
        );
    }

    private ConnectLoginJobListModel createJobModel(
            ConnectJobRecord job,
            ConnectLoginJobListModel.JobListEntryType jobType,
            String appType,
            boolean isAppInstalled,
            boolean isNew,
            boolean isLearningApp,
            boolean isDeliveryApp,
            boolean jobFinished,
            boolean userCompletedDelivery
    ) {
        return new ConnectLoginJobListModel(
                job.getTitle(),
                job.getJobUUID(),
                getAppIdForType(job, jobType),
                job.getProjectEndDate(),
                getDescriptionForType(job, jobType),
                getOrganizationForType(job, jobType),
                isAppInstalled,
                isNew,
                isLearningApp,
                isDeliveryApp,
                processJobRecords(job, jobType),
                job.getLearningPercentComplete(true),
                job.getDeliveryProgressPercentage(),
                jobType,
                appType,
                job,
                jobFinished,
                userCompletedDelivery
        );
    }

    private ConnectAppRecord getAppRecord(
            ConnectJobRecord job,
            ConnectLoginJobListModel.JobListEntryType jobType
    ) {
        return jobType == ConnectLoginJobListModel.JobListEntryType.LEARNING ?
                job.getLearnAppInfo() :
                job.getDeliveryAppInfo();
    }

    private String getAppIdForType(
            ConnectJobRecord job,
            ConnectLoginJobListModel.JobListEntryType jobType
    ) {
        return getAppRecord(job, jobType).getAppId();
    }

    private String getDescriptionForType(
            ConnectJobRecord job,
            ConnectLoginJobListModel.JobListEntryType jobType
    ) {
        return getAppRecord(job, jobType).getDescription();
    }

    private String getOrganizationForType(
            ConnectJobRecord job,
            ConnectLoginJobListModel.JobListEntryType jobType
    ) {
        return getAppRecord(job, jobType).getOrganization();
    }

    public Date processJobRecords(
            ConnectJobRecord job,
            ConnectLoginJobListModel.JobListEntryType jobType
    ) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(requireActivity());

        String appId = getAppRecord(job, jobType).getAppId();

        ConnectLinkedAppRecord appRecord = ConnectAppDatabaseUtil.getConnectLinkedAppRecord(
                requireActivity(), appId, user.getUserId());

        return appRecord != null ? appRecord.getLastAccessed() : new Date();
    }

    @Override
    public String getEndpoint() {
        return ConnectRepository.ENDPOINT_OPPORTUNITIES;
    }

    @Override
    protected @NotNull FragmentConnectJobsListBinding inflateBinding(
            @NotNull LayoutInflater inflater,
            @Nullable ViewGroup container
    ) {
        return FragmentConnectJobsListBinding.inflate(inflater, container, false);
    }
}
