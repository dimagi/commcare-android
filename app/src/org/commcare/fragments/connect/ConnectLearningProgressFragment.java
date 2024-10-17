package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.commcare.android.database.connect.models.ConnectJobAssessmentRecord;
import org.commcare.android.database.connect.models.ConnectJobLearningRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.views.connect.RoundedButton;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

/**
 * Fragment for showing learning progress for a Connect job
 *
 * @author dviggiano
 */
public class ConnectLearningProgressFragment extends Fragment {
    boolean showAppLaunch = true;
    public ConnectLearningProgressFragment() {
        // Required empty public constructor
    }

    public static ConnectLearningProgressFragment newInstance(boolean showAppLaunch) {
        ConnectLearningProgressFragment fragment = new ConnectLearningProgressFragment();
        fragment.showAppLaunch = showAppLaunch;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        getActivity().setTitle(job.getTitle());

        if(getArguments() != null) {
            showAppLaunch = getArguments().getBoolean("showLaunch", true);
        }

        View view = inflater.inflate(R.layout.fragment_connect_learning_progress, container, false);

        RoundedButton refreshButton = view.findViewById(R.id.connect_learning_refresh);
        refreshButton.setOnClickListener(v -> {
            refreshData();
        });

//        updateUpdatedDate(job.getLastLearnUpdate());
        updateUi(view);
        refreshData();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if(ConnectManager.isConnectIdConfigured()) {
            refreshData();
        }
    }

    private void refreshData() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        ConnectManager.updateLearningProgress(getContext(), job, success -> {
            if(success) {
                try {
//                    updateUpdatedDate(new Date());
                    updateUi(null);
                }
                catch(Exception e) {
                    //Ignore exception, happens if we leave the page before API call finishes
                }
            }
        });
    }

    private void updateUi(View view) {
        if(view == null) {
            view = getView();
        }

        if(view == null) {
            return;
        }

        ConnectJobRecord job = ConnectManager.getActiveJob();

        int percent = job.getLearningPercentComplete();
        boolean learningFinished = percent >= 100;
        boolean assessmentAttempted = job.attemptedAssessment();
        boolean assessmentPassed = job.passedAssessment();

        boolean showReviewLearningButton = false;
        String status;
        String buttonText;
        if (learningFinished) {
            if(assessmentAttempted) {
                showReviewLearningButton = true;

                if(assessmentPassed) {
                    status = getString(R.string.connect_learn_finished, job.getAssessmentScore(), job.getLearnAppInfo().getPassingScore());
                    buttonText = getString(R.string.connect_learn_view_details);
                }
                else {
                    status = getString(R.string.connect_learn_failed, job.getAssessmentScore(), job.getLearnAppInfo().getPassingScore());
                    buttonText = getString(R.string.connect_learn_try_again);
                }
            }
            else {
                status = getString(R.string.connect_learn_need_assessment);
                buttonText = getString(R.string.connect_learn_go_to_assessment);
            }
        } else if(percent > 0) {
            status = getString(R.string.connect_learn_status, job.getCompletedLearningModules(),
                    job.getNumLearningModules());
            buttonText = getString(R.string.connect_learn_continue);
        } else {
            status = getString(R.string.connect_learn_not_started);
            buttonText = getString(R.string.connect_learn_start);
        }

        TextView progressText = view.findViewById(R.id.connect_learning_progress_text);
        ProgressBar progressBar = view.findViewById(R.id.connect_learning_progress_bar);
        LinearLayout progressBarTextContainer = view.findViewById(R.id.connect_learn_progress_bar_text_container);

        progressText.setVisibility(assessmentPassed ? View.GONE : View.VISIBLE);
        progressBar.setVisibility(assessmentPassed ? View.GONE : View.VISIBLE);
        progressBarTextContainer.setVisibility(assessmentPassed ? View.GONE : View.VISIBLE);
        if(!assessmentPassed) {
            progressBar.setProgress(percent);
            progressBar.setMax(100);

            progressText.setText(String.format(Locale.getDefault(), "%d%%", percent));
        }

        LinearLayout certContainer = view.findViewById(R.id.connect_learning_certificate_container);
        certContainer.setVisibility(learningFinished && assessmentPassed ? View.VISIBLE : View.GONE);

        int titleResource;
        if(learningFinished) {
            if(assessmentAttempted) {
                if(assessmentPassed) {
                    titleResource = R.string.connect_learn_complete_title;
                }
                else {
                    titleResource = R.string.connect_learn_failed_title;
                }
            }
            else {
                titleResource = R.string.connect_learn_need_assessment_title;
            }
        }
        else {
            titleResource = R.string.connect_learn_progress_title;
        }

        TextView textView = view.findViewById(R.id.connect_learn_progress_title);
        textView.setText(getString(titleResource));

        textView = view.findViewById(R.id.connect_learning_claim_label);
        textView.setVisibility(learningFinished && assessmentPassed ? View.VISIBLE : View.GONE);
        textView.setText(R.string.connect_learn_claim_label);

        textView = view.findViewById(R.id.connect_learning_status_text);
        textView.setText(status);

        textView = view.findViewById(R.id.connect_job_intro_description);
        textView.setText(job.getDescription());

        textView = view.findViewById(R.id.connect_job_intro_title);
        textView.setText(job.getTitle());

        TextView completeByText = view.findViewById(R.id.connect_learning_complete_by_text);
        completeByText.setVisibility(learningFinished && assessmentPassed ? View.GONE : View.VISIBLE);

        boolean finished = job.isFinished();
        textView = view.findViewById(R.id.connect_learning_ended_text);
        textView.setVisibility(finished ? View.VISIBLE : View.GONE);

        if(learningFinished) {
            textView = view.findViewById(R.id.connect_learn_cert_subject);
            textView.setText(job.getTitle());

            textView = view.findViewById(R.id.connect_learn_cert_person);
            textView.setText(ConnectManager.getUser(getContext()).getName());

            Date latestDate = null;
            List<ConnectJobAssessmentRecord> assessments = job.getAssessments();
            if(assessments == null || assessments.size() == 0) {
                for (ConnectJobLearningRecord learning : job.getLearnings()) {
                    if (latestDate == null || latestDate.before(learning.getDate())) {
                        latestDate = learning.getDate();
                    }
                }
            } else {
                for (ConnectJobAssessmentRecord assessment : assessments) {
                    if (latestDate == null || latestDate.before(assessment.getDate())) {
                        latestDate = assessment.getDate();
                    }
                }
            }

            if(latestDate == null) {
                latestDate = new Date();
            }

            textView = view.findViewById(R.id.connect_learn_cert_date);
            textView.setText(getString(R.string.connect_learn_completed, ConnectManager.formatDate(latestDate)));
        } else {
            completeByText.setText(getString(R.string.connect_learn_complete_by, ConnectManager.formatDate(job.getProjectEndDate())));
        }

        final Button reviewButton = view.findViewById(R.id.connect_learning_review_button);
        reviewButton.setVisibility(showAppLaunch && showReviewLearningButton ? View.VISIBLE : View.GONE);
        reviewButton.setText(R.string.connect_learn_review);
        reviewButton.setOnClickListener(v -> {
            NavDirections directions = null;
            if(ConnectManager.isAppInstalled(job.getLearnAppInfo().getAppId())) {
                ConnectManager.launchApp(getContext(), true, job.getLearnAppInfo().getAppId());
                getActivity().finish();
            } else {
                String title = getString(R.string.connect_downloading_learn);
                directions = ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(title, true, true);
            }

            if(directions != null) {
                Navigation.findNavController(reviewButton).navigate(directions);
            }
        });

        final Button button = view.findViewById(R.id.connect_learning_button);
        button.setVisibility(showAppLaunch ? View.VISIBLE : View.GONE);
        button.setText(buttonText);
        button.setOnClickListener(v -> {
            NavDirections directions = null;
            if(learningFinished && assessmentPassed) {
                directions = ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectJobDeliveryDetailsFragment(true);
            } else if(ConnectManager.isAppInstalled(job.getLearnAppInfo().getAppId())) {
                ConnectManager.launchApp(getContext(), true, job.getLearnAppInfo().getAppId());
                getActivity().finish();
            } else {
                String title = getString(R.string.connect_downloading_learn);
                directions = ConnectLearningProgressFragmentDirections.actionConnectJobLearningProgressFragmentToConnectDownloadingFragment(title, true, true);
            }

            if(directions != null) {
                Navigation.findNavController(button).navigate(directions);
            }
        });
    }

//    private void updateUpdatedDate(Date lastUpdate) {
//        View view = getView();
//        if(view == null) {
//            return;
//        }
//
//        TextView updateText = view.findViewById(R.id.connect_learning_last_update);
//        updateText.setText(getString(R.string.connect_last_update, ConnectManager.formatDateTime(lastUpdate)));
//    }
}
