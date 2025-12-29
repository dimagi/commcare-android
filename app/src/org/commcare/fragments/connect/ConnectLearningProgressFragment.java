package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.navigation.Navigation;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectAppUtils;
import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.connect.PersonalIdManager;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectLearningProgressBinding;
import org.commcare.dalvik.databinding.ViewJobCardBinding;
import org.commcare.fragments.RefreshableFragment;
import org.commcare.modern.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON;

public class ConnectLearningProgressFragment extends ConnectJobFragment<FragmentConnectLearningProgressBinding>
        implements RefreshableFragment {

    private boolean showAppLaunch = true;

    public static ConnectLearningProgressFragment newInstance(boolean showAppLaunch) {
        ConnectLearningProgressFragment fragment = new ConnectLearningProgressFragment();
        fragment.showAppLaunch = showAppLaunch;
        return fragment;
    }

    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (getArguments() != null) {
            showAppLaunch = getArguments().getBoolean(SHOW_LAUNCH_BUTTON, true);
        }

        requireActivity().setTitle(getString(R.string.connect_learn_title));
        setupRefreshButton();
        populateJobCard(job);
        refreshLearningData();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (PersonalIdManager.getInstance().isloggedIn()) {
            refreshLearningData();
        }
    }

    @Override
    public void refresh() {
        refreshLearningData();
    }

    private void setupRefreshButton() {
        getBinding().btnSync.setOnClickListener(v -> refreshLearningData());
    }

    private void refreshLearningData() {
        ConnectJobHelper.INSTANCE.updateLearningProgress(requireContext(), job, success -> {
            if (success && isAdded()) {
                updateLearningUI();
            } else if (!success && isAdded()) {
                Toast.makeText(
                        requireContext(),
                        getString(R.string.connect_fetch_learning_progress_error),
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void updateLearningUI() {
        int progressPercent = job.getLearningPercentComplete();
        boolean learningComplete = progressPercent >= 100;
        boolean hasAttempted = job.attemptedAssessment();
        boolean hasPassed = job.passedAssessment();

        updateProgressViews(progressPercent, hasPassed);
        updateCertificateView(job, learningComplete, hasPassed);
        updateButtons(job, learningComplete, hasPassed);
        updateLearningStatus(job, learningComplete, hasPassed, hasAttempted);
    }

    private void updateProgressViews(int percent, boolean hideProgress) {
        int visibility = hideProgress ? View.GONE : View.VISIBLE;
        getBinding().connectLearningProgressBar.setVisibility(visibility);
        getBinding().connectLearningProgressText.setVisibility(visibility);
        getBinding().connectLearnProgressBarTextContainer.setVisibility(visibility);
        getBinding().learningCard.setVisibility(visibility);

        if (!hideProgress) {
            getBinding().connectLearningProgressBar.setProgress(percent);
            getBinding().connectLearningProgressText.setText(String.format(Locale.getDefault(), "%d%%", percent));
        }
    }

    private void updateCertificateView(ConnectJobRecord job, boolean complete, boolean passed) {
        getBinding().connectLearningCertificateContainer.setVisibility(
                complete && passed ? View.VISIBLE : View.GONE);

        if (complete && passed) {
            getBinding().connectLearnCertSubject.setText(job.getTitle());
            getBinding().connectLearnCertPerson.setText(ConnectUserDatabaseUtil.getUser(requireContext()).getName());

            Date latestDate = getLatestCompletionDate(job);
            getBinding().connectLearnCertDate.setText(
                    getString(R.string.connect_learn_completed,
                            ConnectDateUtils.INSTANCE.formatDate(latestDate)));
        }
    }

    private Date getLatestCompletionDate(ConnectJobRecord job) {
        List<ConnectJobAssessmentRecord> assessments = job.getAssessments();
        Date latestDate = null;

        if (assessments != null && !assessments.isEmpty()) {
            for (ConnectJobAssessmentRecord a : assessments) {
                if (latestDate == null || latestDate.before(a.getDate())) {
                    latestDate = a.getDate();
                }
            }
        } else {
            for (ConnectJobLearningRecord l : job.getLearnings()) {
                if (latestDate == null || latestDate.before(l.getDate())) {
                    latestDate = l.getDate();
                }
            }
        }

        return latestDate != null ? latestDate : new Date();
    }

    private void updateButtons(ConnectJobRecord job, boolean complete, boolean passed) {
        getBinding().connectLearningReviewButton.setVisibility(View.GONE); // reserved for future logic
        getBinding().connectLearningButton.setVisibility(showAppLaunch ? View.VISIBLE : View.GONE);

        if (showAppLaunch) {
            if(complete && passed) {
                configureJobDetailsButton();
            } else if(!AppUtils.isAppInstalled(job.getLearnAppInfo().getAppId())) {
                //This case needs to come before any that would launch the learn app
                configureDownloadButton();
            } else if(!complete) {
                configureLaunchLearningButton();
            } else {
                configureGoToAssessmentButton();
            }
        }
    }

    private void configureJobDetailsButton() {
        getBinding().connectLearningButton.setText(getString(R.string.connect_learn_view_details));
        getBinding().connectLearningButton.setOnClickListener(
                v -> Navigation.findNavController(v).navigate(ConnectLearningProgressFragmentDirections
                        .actionConnectJobLearningProgressFragmentToConnectJobDeliveryDetailsFragment(
                        true)));
    }

    private void configureGoToAssessmentButton() {
        getBinding().connectLearningButton.setText(getString(R.string.connect_learn_go_to_assessment));
        getBinding().connectLearningButton.setOnClickListener(v -> navigateToLearnAppHome());
    }

    private void configureLaunchLearningButton() {
        getBinding().connectLearningButton.setText(getString(R.string.connect_learn_continue));
        getBinding().connectLearningButton.setOnClickListener(v -> navigateToLearnAppHome());
    }

    private void configureDownloadButton() {
        getBinding().connectLearningButton.setText(getString(R.string.connect_download_learn));
        getBinding().connectLearningButton.setOnClickListener(
                v -> Navigation.findNavController(v).navigate(ConnectLearningProgressFragmentDirections
                        .actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(
                                getString(R.string.connect_downloading_learn), true)));
    }

    private void updateLearningStatus(ConnectJobRecord job, boolean complete, boolean passed, boolean attempted) {
        Pair<Integer, String> status = getLearningStatus(job, complete, passed, attempted);
        getBinding().connectLearnProgressTitle.setText(getString(status.first));
        getBinding().connectLearningStatusText.setText(status.second);

        getBinding().connectLearningEndedText.setVisibility(job.isFinished() ? View.VISIBLE : View.GONE);
    }

    private Pair<Integer, String> getLearningStatus(ConnectJobRecord job, boolean learningComplete,
                                                    boolean passedAssessment, boolean attemptedAssessment) {
        if (learningComplete) {
            if (attemptedAssessment) {
                if (passedAssessment) {
                    return new Pair<>(R.string.connect_learn_complete_title,
                            getString(R.string.connect_learn_finished, job.getAssessmentScore(),
                                    job.getLearnAppInfo().getPassingScore()));
                }

                return new Pair<>(R.string.connect_learn_failed_title,
                        getString(R.string.connect_learn_failed, job.getAssessmentScore(),
                                job.getLearnAppInfo().getPassingScore()));
            }

            return new Pair<>(R.string.connect_learn_need_assessment_title,
                        getString(R.string.connect_learn_need_assessment));
        }

        if (job.getLearningPercentComplete() > 0) {
            return new Pair<>(R.string.connect_learn_progress_title,
                    getString(R.string.connect_learn_status, job.getCompletedLearningModules(),
                            job.getNumLearningModules()));
        }

        return new Pair<>(R.string.connect_learn_progress_title,
                getString(R.string.connect_learn_not_started));
    }

    private void populateJobCard(ConnectJobRecord job) {
        ViewJobCardBinding jobCard = getBinding().viewJobCard;

        jobCard.tvJobTitle.setText(job.getTitle());
        jobCard.tvJobDescription.setText(job.getDescription());

        String dateMessage;
        if (job.deliveryComplete()) {
            dateMessage = getString(
                    R.string.connect_job_ended,
                    ConnectDateUtils.INSTANCE.formatDateForCompletedJob(job.getProjectEndDate())
            );
        } else {
            dateMessage = getString(
                    R.string.connect_learn_complete_by,
                    ConnectDateUtils.INSTANCE.formatDate(job.getProjectEndDate())
            );
        }

        jobCard.connectJobEndDateSubHeading.setText(dateMessage);

        String hours = job.getWorkingHours();
        boolean showHours = hours != null;
        jobCard.tvJobTime.setVisibility(showHours ? View.VISIBLE : View.GONE);
        jobCard.tvDailyVisitTitle.setVisibility(showHours ? View.VISIBLE : View.GONE);
        jobCard.tvJobDescription.setVisibility(View.INVISIBLE);
        jobCard.connectJobEndDateSubHeading.setVisibility(View.VISIBLE);
        jobCard.connectJobEndDate.setVisibility(View.GONE);
        jobCard.mbViewInfo.setOnClickListener(this::navigateToJobDetailBottomSheet);
        jobCard.mbResume.setOnClickListener(v -> navigateToLearnAppHome());
        jobCard.tvViewMore.setVisibility(View.GONE);
        jobCard.mbViewInfo.setVisibility(View.VISIBLE);
        jobCard.mbResume.setVisibility(View.VISIBLE);

        if (showHours) {
            jobCard.tvJobTime.setText(hours);
        }
    }

    private void navigateToJobDetailBottomSheet(View view) {
        Navigation.findNavController(view).navigate(
                ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectJobDetailBottomSheetDialogFragment());
    }

    private void navigateToLearnAppHome() {
        CommCareApplication.instance().closeUserSession();
        ConnectAppUtils.INSTANCE.launchApp(requireActivity(), true, job.getLearnAppInfo().getAppId());
    }

    @Override
    protected @NotNull FragmentConnectLearningProgressBinding inflateBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentConnectLearningProgressBinding.inflate(inflater, container, false);
    }
}
