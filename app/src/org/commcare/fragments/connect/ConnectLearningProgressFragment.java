package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import org.commcare.AppUtils;
import org.commcare.connect.ConnectAppLaunchController;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.PersonalIdOrConnectApiErrorHandler;
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.connect.repository.ConnectRepository;
import org.commcare.connect.repository.DataState;
import org.commcare.connect.viewmodel.ConnectLearningProgressViewModel;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectLearningProgressBinding;
import org.commcare.dalvik.databinding.ViewJobCardBinding;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.modern.util.Pair;
import org.commcare.views.connect.ConnectViewUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON;
import static org.commcare.fragments.extensions.FragmentExtensionsKt.hasLiveView;

public class ConnectLearningProgressFragment extends ConnectJobFragment<FragmentConnectLearningProgressBinding>
        implements RefreshableFragment {

    private boolean showAppLaunch = true;
    private ConnectLearningProgressViewModel viewModel;

    public static ConnectLearningProgressFragment newInstance(boolean showAppLaunch) {
        ConnectLearningProgressFragment fragment = new ConnectLearningProgressFragment();
        fragment.showAppLaunch = showAppLaunch;
        return fragment;
    }

    @Override
    public @NotNull View onCreateView(
            @NotNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (getArguments() != null) {
            showAppLaunch = getArguments().getBoolean(SHOW_LAUNCH_BUTTON, true);
        }

        requireActivity().setTitle(getString(R.string.connect_learn_title));
        setWaitDialogEnabled(false);
        viewModel = new ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().getApplication())
        ).get(ConnectLearningProgressViewModel.class);

        setupRefreshButton();
        populateJobCard();
        updateLearningUI();
        observeLearningProgress();
        observeClaimJob();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PersonalIdManager.getInstance().isloggedIn()) {
            refresh(false);
        }
    }

    @Override
    public void refresh(boolean forceRefresh) {
        viewModel.loadLearningProgress(job, forceRefresh);
    }

    private void setupRefreshButton() {
        getBinding().btnSync.setOnClickListener(v -> refresh(true));
    }

    private void observeLearningProgress() {
        observeDataState(
                viewModel.getLearningProgress(),
                cached -> {
                    setActiveJob(cached);
                    updateLearningUI();
                },
                success -> {
                    setActiveJob(success);
                    updateLearningUI();
                }
        );
    }

    private void updateLearningUI() {
        boolean learningComplete = job.getLearningPercentComplete(false) >= 100;
        boolean showLearningComplete = learningComplete && job.passedAssessment();

        if (showLearningComplete) {
            getBinding().progressContainer.setVisibility(View.GONE);
            getBinding().learnCompleteView.setVisibility(View.VISIBLE);
            getBinding().learnCompleteView.bind(
                    job,
                    job.getLearningCompletionDate(),
                    ConnectUserDatabaseUtil.getUser(requireContext()).getName(),
                    this::onDeliveryCtaClicked
            );
        } else {
            getBinding().progressContainer.setVisibility(View.VISIBLE);
            getBinding().learnCompleteView.setVisibility(View.GONE);
            updateProgressViews(job.getLearningPercentComplete(true));
            updateButtons(learningComplete);
            updateLearningStatus(learningComplete, job.attemptedAssessment());
        }
    }

    private void updateProgressViews(int learningProgressPercent) {
        getBinding().connectLearningProgressBar.setProgress(learningProgressPercent);
        getBinding().connectLearningProgressText.setText(
                String.format(Locale.getDefault(), "%d%%", learningProgressPercent)
        );
    }

    private void onDeliveryCtaClicked(View view) {
        getBinding().learnCompleteView.hideClaimFailure();
        getBinding().learnCompleteView.setCtaEnabled(false);
        viewModel.claimJob(job);
    }

    private void observeClaimJob() {
        viewModel.getClaimJob().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof DataState.Success) {
                FirebaseAnalyticsUtil.reportCccApiClaimJob(true);
                if (!hasLiveView(this)) {
                    return;
                }
                boolean deliveryAppInstalled = AppUtils.isAppInstalled(job.getDeliveryAppInfo().getAppId());
                Navigation.findNavController(requireView()).navigate(
                        deliveryAppInstalled
                                ? navigateToDeliveryProgress()
                                : navigateToDeliveryDownload()
                );
            } else if (state instanceof DataState.Error) {
                FirebaseAnalyticsUtil.reportCccApiClaimJob(false);
                DataState.Error<?> error = (DataState.Error<?>) state;
                if (!hasLiveView(this)) {
                    return;
                }
                getBinding().learnCompleteView.setCtaEnabled(true);
                PersonalIdOrConnectApiErrorCodes errorCode = error.getErrorCode();
                String message = errorCode == PersonalIdOrConnectApiErrorCodes.BAD_REQUEST_ERROR
                        ? getString(R.string.recovery_unable_to_claim_opportunity)
                        : PersonalIdOrConnectApiErrorHandler.handle(requireContext(), errorCode, error.getThrowable());
                getBinding().learnCompleteView.showClaimFailure(message);
            }
        });
    }

    private NavDirections navigateToDeliveryProgress() {
        return ConnectLearningProgressFragmentDirections
                .actionConnectJobLearningProgressFragmentToConnectJobDeliveryProgressFragment();
    }

    private NavDirections navigateToDeliveryDownload() {
        return ConnectLearningProgressFragmentDirections
                .actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(
                        getString(R.string.connect_downloading_delivery),
                        false
                );
    }

    private void updateButtons(boolean learningComplete) {
        getBinding().connectLearningReviewButton.setVisibility(View.GONE); // reserved for future logic
        getBinding().connectLearningButton.setVisibility(showAppLaunch ? View.VISIBLE : View.GONE);

        if (showAppLaunch) {
            if (!AppUtils.isAppInstalled(job.getLearnAppInfo().getAppId())) {
                // This case needs to come before any that would launch the learn app
                configureDownloadButton();
            } else if (!learningComplete) {
                configureLaunchLearningButton();
            } else {
                configureGoToAssessmentButton();
            }
        }
    }

    private void configureGoToAssessmentButton() {
        getBinding().connectLearningButton.setText(
                getString(R.string.connect_learn_go_to_assessment)
        );
        getBinding().connectLearningButton.setOnClickListener(v -> navigateToLearnAppHome());
    }

    private void configureLaunchLearningButton() {
        getBinding().connectLearningButton.setText(getString(R.string.connect_learn_continue));
        getBinding().connectLearningButton.setOnClickListener(v -> navigateToLearnAppHome());
    }

    private void configureDownloadButton() {
        getBinding().connectLearningButton.setText(getString(R.string.connect_download_learn));
        getBinding().connectLearningButton.setOnClickListener(
                v -> Navigation.findNavController(v).navigate(
                        ConnectLearningProgressFragmentDirections
                                .actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(
                                        getString(R.string.connect_downloading_learn),
                                        true
                                )
                )
        );
    }

    private void updateLearningStatus(boolean learningComplete, boolean attemptedAssessment) {
        Pair<Integer, String> status = getLearningStatus(learningComplete, attemptedAssessment);
        getBinding().connectLearnProgressTitle.setText(getString(status.first));
        getBinding().connectLearningStatusText.setText(status.second);

        getBinding().connectLearningEndedText.setVisibility(job.isFinished() ? View.VISIBLE : View.GONE);
    }

    private Pair<Integer, String> getLearningStatus(boolean learningComplete, boolean attemptedAssessment) {
        if (learningComplete) {
            if (attemptedAssessment) {
                return new Pair<>(
                        R.string.connect_learn_failed_title,
                        getString(
                                R.string.connect_learn_failed,
                                job.getAssessmentScore(),
                                job.getLearnAppInfo().getPassingScore()
                        )
                );
            }

            return new Pair<>(
                    R.string.connect_learn_need_assessment_title,
                    getString(R.string.connect_learn_need_assessment)
            );
        }

        if (job.getLearningPercentComplete(false) > 0) {
            return new Pair<>(
                    R.string.connect_learn_progress_title,
                    getString(
                            R.string.connect_learn_status,
                            job.getCompletedLearningModules(),
                            job.getNumLearningModules()
                    )
            );
        }

        return new Pair<>(
                R.string.connect_learn_progress_title,
                getString(R.string.connect_learn_not_started)
        );
    }

    private void populateJobCard() {
        ViewJobCardBinding jobCard = getBinding().viewJobCard;
        boolean appInstalled = AppUtils.isAppInstalled(job.getLearnAppInfo().getAppId());

        ConnectViewUtils.setupCardViewForJob(
                jobCard,
                job,
                appInstalled,
                v -> navigateToLearnAppHome(),
                this::navigateToJobDetailBottomSheet
        );
    }

    private void navigateToJobDetailBottomSheet(View view) {
        Navigation.findNavController(view).navigate(
                ConnectLearningProgressFragmentDirections
                        .actionConnectJobLearningProgressFragmentToConnectJobDetailBottomSheetDialogFragment()
        );
    }

    private void navigateToLearnAppHome() {
        String appId = job.getLearnAppInfo().getAppId();

        if (AppUtils.isAppInstalled(appId)) {
            new ConnectAppLaunchController(this).launchApp(appId, true, this::popSelfOnceHidden);
        } else {
            NavDirections navDirections = ConnectLearningProgressFragmentDirections
                    .actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(
                            getString(R.string.connect_downloading_learn),
                            true
                    );
            Navigation.findNavController(getBinding().getRoot()).navigate(navDirections);
        }
    }

    @Override
    public String getEndpoint() {
        return ConnectRepository.SYNC_KEY_LEARNING_PREFIX + job.getJobUUID();
    }

    @Override
    protected @NotNull FragmentConnectLearningProgressBinding inflateBinding(
            @NotNull LayoutInflater inflater,
            @Nullable ViewGroup container
    ) {
        return FragmentConnectLearningProgressBinding
                .inflate(inflater, container, false);
    }
}
