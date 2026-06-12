package org.commcare.fragments.connect;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.DispatchActivity;
import org.commcare.activities.HomeScreenBaseActivity;
import org.commcare.activities.connect.ConnectActivity;
import org.commcare.adapters.JobListConnectHomeAppsAdapter;
import org.commcare.android.database.connect.models.ConnectAppRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectAppLauncher;
import org.commcare.connect.ConnectAppUtils;
import org.commcare.connect.LaunchActions;
import org.commcare.connect.LaunchOutcome;
import org.commcare.connect.LaunchOutcomeRouter;
import org.commcare.connect.database.ConnectAppDatabaseUtil;
import org.commcare.connect.network.TokenExceptionHandler;
import org.commcare.connect.repository.ConnectRepository;
import org.commcare.connect.database.ConnectJobUtils;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.viewmodel.ConnectJobsListViewModel;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectJobsListBinding;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.fragments.base.BaseConnectFragment;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.interfaces.OnJobSelectionClick;
import org.commcare.login.LoginPhase;
import org.commcare.login.LoginProgress;
import org.commcare.models.connect.ConnectLoginJobListModel;
import org.commcare.util.LogTypes;
import org.commcare.views.dialogs.CustomProgressDialog;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
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
    private ConnectJobsListViewModel viewModel;

    private static final String LAUNCH_DIALOG_TAG = "connect_launch_progress";
    // Negative so it can't collide with the positive task ids CommCareActivity assigns to real tasks.
    private static final int LAUNCH_DIALOG_TASK_ID = -10;
    private static final int PROGRESS_BAR_MAX = 100;
    private CustomProgressDialog launchDialog;
    private boolean showingSyncDialog;

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
        setWaitDialogEnabled(false);
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
                this::setJobListData,
                this::setJobListData
        );
    }

    private void initRecyclerView() {
        boolean noJobsAvailable = inProgressJobs.isEmpty()
                && newJobs.isEmpty() && finishedJobs.isEmpty();
        getBinding().connectNoJobsText.setVisibility(noJobsAvailable ? View.VISIBLE : View.GONE);

        JobListConnectHomeAppsAdapter adapter = new JobListConnectHomeAppsAdapter(
                getContext(),
                inProgressJobs,
                newJobs,
                finishedJobs,
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
            launchApp(isLearning, appId);
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

    private void launchApp(boolean isLearning, String appId) {
        FragmentActivity activity = requireActivity();
        showLaunchDialog(false);
        new ConnectAppLauncher().start(
                getViewLifecycleOwner(),
                activity,
                appId,
                isLearning,
                progress -> activity.runOnUiThread(() -> updateLaunchProgress(progress)),
                outcome -> handleLaunchOutcome(outcome, activity, isLearning, appId)
        );
    }

    private void updateLaunchProgress(LoginProgress progress) {
        if (!isAdded()) {
            return;
        }

        LoginPhase phase = progress.getPhase();
        boolean syncing = phase == LoginPhase.Syncing;
        if (launchDialog == null || syncing != showingSyncDialog) {
            showLaunchDialog(syncing);
        }
        if (phase == LoginPhase.Seating) {
            launchDialog.updateTitle(Localization.get("seating.app"));
            launchDialog.updateMessage(Localization.get("seating.app"));
        } else if (phase == LoginPhase.SigningIn) {
            launchDialog.updateTitle(Localization.get("key.manage.title"));
            launchDialog.updateMessage(Localization.get("key.manage.start"));
        }
        if (progress.getMessage() != null) {
            launchDialog.updateMessage(progress.getMessage());
        }
        Integer percent = progress.getPercent();
        if (syncing && percent != null) {
            launchDialog.updateProgressBar(percent, PROGRESS_BAR_MAX);
        }
    }

    private void handleLaunchOutcome(
            LaunchOutcome outcome,
            FragmentActivity activity,
            boolean isLearning,
            String appId
    ) {
        LaunchOutcomeRouter.INSTANCE.dispatch(outcome, new LaunchActions() {
            @Override
            public void dismissProgress() {
                dismissLaunchDialog();
            }

            @Override
            public void launchHome() {
                HomeScreenBaseActivity.launchHome(activity);
            }

            @Override
            public void handleTokenDenied() {
                TokenExceptionHandler.INSTANCE.handleTokenDeniedException();
            }

            @Override
            public void recoverFromSeatFailure() {
                startDispatchAfterSeatFailure(activity);
            }

            @Override
            public void fallBackToLegacyLaunch() {
                ConnectAppUtils.INSTANCE.launchApp(activity, isLearning, appId);
            }

            @Override
            public void reportFailure(String reason) {
                reportLaunchFailure(appId, reason);
            }
        });
    }

    private void startDispatchAfterSeatFailure(FragmentActivity activity) {
        Intent intent = new Intent(activity, DispatchActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        activity.finish();
    }

    private void reportLaunchFailure(String appId, String reason) {
        Logger.log(
                LogTypes.TYPE_ERROR_WORKFLOW,
                "Connect launch failed for app " + appId + ": " + reason
        );
        FirebaseAnalyticsUtil.reportCccAppFailedAutoLogin(appId);
    }

    private void showLaunchDialog(boolean syncing) {
        if (getChildFragmentManager().isStateSaved()) {
            return;
        }
        dismissLaunchDialog();
        if (syncing) {
            launchDialog = CustomProgressDialog.newInstance(
                    Localization.get("sync.communicating.title"),
                    Localization.get("sync.progress.starting"),
                    LAUNCH_DIALOG_TASK_ID
            );
            launchDialog.addProgressBar();
        } else {
            launchDialog = CustomProgressDialog.newInstance(
                    Localization.get("seating.app"),
                    Localization.get("seating.app"),
                    LAUNCH_DIALOG_TASK_ID
            );
        }
        showingSyncDialog = syncing;
        launchDialog.showNow(getChildFragmentManager(), LAUNCH_DIALOG_TAG);
    }

    private void dismissLaunchDialog() {
        if (launchDialog != null) {
            if (launchDialog.isAdded()) {
                launchDialog.dismissAllowingStateLoss();
            }
            launchDialog = null;
        }
    }

    @Override
    public void onDestroyView() {
        dismissLaunchDialog();
        super.onDestroyView();
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
        return ConnectRepository.SYNC_KEY_OPPORTUNITIES;
    }

    @Override
    protected @NotNull FragmentConnectJobsListBinding inflateBinding(
            @NotNull LayoutInflater inflater,
            @Nullable ViewGroup container
    ) {
        return FragmentConnectJobsListBinding.inflate(inflater, container, false);
    }
}
