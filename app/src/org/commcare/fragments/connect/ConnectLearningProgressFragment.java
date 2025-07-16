package org.commcare.fragments.connect;

import static org.commcare.connect.ConnectConstants.SHOW_LAUNCH_BUTTON;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavDirections;
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

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConnectLearningProgressFragment extends ConnectJobFragment {

    private boolean showAppLaunch = true;
    private @NonNull FragmentConnectLearningProgressBinding viewBinding;

    public static ConnectLearningProgressFragment newInstance(boolean showAppLaunch) {
        ConnectLearningProgressFragment fragment = new ConnectLearningProgressFragment();
        fragment.showAppLaunch = showAppLaunch;
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (getArguments() != null) {
            showAppLaunch = getArguments().getBoolean(SHOW_LAUNCH_BUTTON, true);
        }
        viewBinding = FragmentConnectLearningProgressBinding.inflate(inflater, container, false);
        setupToolbar();
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

    private void setupToolbar() {
        requireActivity().setTitle(getString(R.string.connect_learn_title));
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.action_sync) {
                    refreshLearningData();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
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

        if (!showAppLaunch) {
            viewBinding.connectLearningButton.setVisibility(View.GONE);
            return;
        }

        String buttonText;
        NavDirections navDirections = null;

        if (complete && passed) {
            viewBinding.connectLearningButton.setVisibility(View.VISIBLE);
            buttonText = getString(R.string.connect_learn_view_details);
            navDirections =
                    ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectJobDeliveryDetailsFragment(
                            true);
        } else if (AppUtils.isAppInstalled(job.getLearnAppInfo().getAppId())) {
            buttonText = complete ? getString(R.string.connect_learn_go_to_assessment) : getString(
                    R.string.connect_learn_continue);
            viewBinding.connectLearningButton.setVisibility(View.VISIBLE);
            viewBinding.connectLearningButton.setOnClickListener(v -> {
                CommCareApplication.instance().closeUserSession();
                ConnectAppUtils.INSTANCE.launchApp(getActivity(), true, job.getLearnAppInfo().getAppId());
            });
            return;
        } else {
            buttonText = getString(R.string.connect_downloading_learn);
            navDirections =
                    ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(
                            buttonText, true);
        }

        viewBinding.connectLearningButton.setText(buttonText);
        if (navDirections != null) {
            NavDirections finalDirections = navDirections;
            viewBinding.connectLearningButton.setOnClickListener(
                    v -> Navigation.findNavController(v).navigate(finalDirections));
        }
    }

    private void updateLearningStatus(ConnectJobRecord job, boolean complete, boolean passed, boolean attempted) {
        int titleRes;
        String status;

        if (complete) {
            if (attempted) {
                if (passed) {
                    titleRes = R.string.connect_learn_complete_title;
                    status = getString(R.string.connect_learn_finished, job.getAssessmentScore(),
                            job.getLearnAppInfo().getPassingScore());
                } else {
                    titleRes = R.string.connect_learn_failed_title;
                    status = getString(R.string.connect_learn_failed, job.getAssessmentScore(),
                            job.getLearnAppInfo().getPassingScore());
                }
            } else {
                titleRes = R.string.connect_learn_need_assessment_title;
                status = getString(R.string.connect_learn_need_assessment);
            }
        } else if (job.getLearningPercentComplete() > 0) {
            titleRes = R.string.connect_learn_progress_title;
            status = getString(R.string.connect_learn_status, job.getCompletedLearningModules(),
                    job.getNumLearningModules());
        } else {
            titleRes = R.string.connect_learn_progress_title;
            status = getString(R.string.connect_learn_not_started);
        }

        viewBinding.connectLearnProgressTitle.setText(getString(titleRes));
        viewBinding.connectLearningStatusText.setText(status);
        viewBinding.connectLearningEndedText.setVisibility(job.isFinished() ? View.VISIBLE : View.GONE);
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
