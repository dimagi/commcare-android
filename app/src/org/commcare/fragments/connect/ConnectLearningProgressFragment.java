package org.commcare.fragments.connect;

import static org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.camera.core.processing.SurfaceProcessorNode;
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

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConnectLearningProgressFragment extends ConnectJobFragment
        implements RefreshableFragment {

    private boolean showAppLaunch = true;
    private FragmentConnectLearningProgressBinding viewBinding;

    public static ConnectLearningProgressFragment newInstance(boolean showAppLaunch) {
        ConnectLearningProgressFragment fragment = new ConnectLearningProgressFragment();
        fragment.showAppLaunch = showAppLaunch;
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (getArguments() != null) {
            showAppLaunch = getArguments().getBoolean(SHOW_LAUNCH_BUTTON, true);
        }

        viewBinding = FragmentConnectLearningProgressBinding.inflate(inflater, container, false);
        requireActivity().setTitle(getString(R.string.connect_learn_title));
        setupRefreshButton();
        populateJobCard(job);
        refreshLearningData();

        return viewBinding.getRoot();
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
        viewBinding.btnSync.setOnClickListener(v -> refreshLearningData());
    }

    private void refreshLearningData() {
        ConnectJobHelper.INSTANCE.updateLearningProgress(getContext(), job, success -> {
            if (success && isAdded()) {
                updateLearningUI();
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
        viewBinding.connectLearningProgressBar.setVisibility(visibility);
        viewBinding.connectLearningProgressText.setVisibility(visibility);
        viewBinding.connectLearnProgressBarTextContainer.setVisibility(visibility);

        if (!hideProgress) {
            viewBinding.connectLearningProgressBar.setProgress(percent);
            viewBinding.connectLearningProgressText.setText(String.format(Locale.getDefault(), "%d%%", percent));
        }
    }

    private void updateCertificateView(ConnectJobRecord job, boolean complete, boolean passed) {
        viewBinding.connectLearningCertificateContainer.setVisibility(
                complete && passed ? View.VISIBLE : View.GONE);

        if (complete && passed) {
            viewBinding.connectLearnCertSubject.setText(job.getTitle());
            viewBinding.connectLearnCertPerson.setText(ConnectUserDatabaseUtil.getUser(getContext()).getName());

            Date latestDate = getLatestCompletionDate(job);
            viewBinding.connectLearnCertDate.setText(
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
        viewBinding.connectLearningReviewButton.setVisibility(View.GONE); // reserved for future logic
        viewBinding.connectLearningButton.setVisibility(showAppLaunch ? View.VISIBLE : View.GONE);

        if (showAppLaunch) {
            if (complete && passed) {
                configureJobDetailsButton();
            } else if (AppUtils.isAppInstalled(job.getLearnAppInfo().getAppId())) {
                configureLaunchLearningButton();
            } else {
                configureDownloadButton();
            }
        }
    }

    private void configureJobDetailsButton() {
        viewBinding.connectLearningButton.setText(getString(R.string.connect_learn_view_details));
        viewBinding.connectLearningButton.setOnClickListener(
                v -> Navigation.findNavController(v).navigate(ConnectLearningProgressFragmentDirections
                        .actionConnectJobLearningProgressFragmentToConnectJobDeliveryDetailsFragment(
                        true)));
    }

    private void configureLaunchLearningButton() {
        viewBinding.connectLearningButton.setText(getString(R.string.connect_learn_continue));
        viewBinding.connectLearningButton.setOnClickListener(v -> {
            CommCareApplication.instance().closeUserSession();
            ConnectAppUtils.INSTANCE.launchApp(requireActivity(), true, job.getLearnAppInfo().getAppId());
        });
    }

    private void configureDownloadButton() {
        viewBinding.connectLearningButton.setText(getString(R.string.connect_download_learn));
        viewBinding.connectLearningButton.setOnClickListener(
                v -> Navigation.findNavController(v).navigate(ConnectLearningProgressFragmentDirections
                        .actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(
                                getString(R.string.connect_downloading_learn), true)));
    }

    private void updateLearningStatus(ConnectJobRecord job, boolean complete, boolean passed, boolean attempted) {
        Pair<Integer, String> status = getLearningStatus(job, complete, passed, attempted);
        viewBinding.connectLearnProgressTitle.setText(getString(status.first));
        viewBinding.connectLearningStatusText.setText(status.second);

        viewBinding.connectLearningEndedText.setVisibility(job.isFinished() ? View.VISIBLE : View.GONE);
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
        ViewJobCardBinding jobCard = viewBinding.viewJobCard;

        jobCard.tvJobTitle.setText(job.getTitle());
        jobCard.tvJobDescription.setText(job.getDescription());
        jobCard.connectJobEndDate.setText(
                getString(R.string.connect_learn_complete_by,
                        ConnectDateUtils.INSTANCE.formatDate(job.getProjectEndDate())));

        String hours = job.getWorkingHours();
        boolean showHours = hours != null;
        jobCard.tvJobTime.setVisibility(showHours ? View.VISIBLE : View.GONE);
        jobCard.tvDailyVisitTitle.setVisibility(showHours ? View.VISIBLE : View.GONE);

        if (showHours) {
            jobCard.tvJobTime.setText(hours);
        }
    }
}
