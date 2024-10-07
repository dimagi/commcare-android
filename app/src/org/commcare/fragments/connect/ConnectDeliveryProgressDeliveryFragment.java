package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.commcare.activities.CommCareActivity;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.views.connect.CircleProgressBar;
import org.commcare.views.connect.RoundedButton;
import org.commcare.views.connect.connecttextview.ConnectMediumTextView;
import org.commcare.views.dialogs.StandardAlertDialog;
import org.javarosa.core.services.locale.Localization;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

public class ConnectDeliveryProgressDeliveryFragment extends Fragment {
    private View view;
    private boolean showLearningLaunch = true;
    private boolean showDeliveryLaunch = true;

    private RoundedButton launchButton;

    public ConnectDeliveryProgressDeliveryFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressDeliveryFragment newInstance(boolean showLearningLaunch, boolean showDeliveryLaunch) {
        ConnectDeliveryProgressDeliveryFragment fragment = new ConnectDeliveryProgressDeliveryFragment();
        fragment.showLearningLaunch = showLearningLaunch;
        fragment.showDeliveryLaunch = showDeliveryLaunch;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_connect_progress_delivery, container, false);

        launchButton = view.findViewById(R.id.connect_progress_button);
        launchButton.setVisibility(showDeliveryLaunch ? View.VISIBLE : View.GONE);

        launchButton.setOnClickListener(v -> {
            launchDeliveryApp(launchButton);
        });

        RoundedButton btnSync = view.findViewById(R.id.btnSync);
        btnSync.setOnClickListener(view -> {
            ConnectDeliveryProgressFragment parentFragment = (ConnectDeliveryProgressFragment) getParentFragment();
            if (parentFragment != null) {
                parentFragment.refreshData();
            }
        });

        RoundedButton reviewButton = view.findViewById(R.id.connect_progress_review_button);
        reviewButton.setVisibility(showLearningLaunch ? View.VISIBLE : View.GONE);
        reviewButton.setOnClickListener(v -> {
            launchLearningApp(reviewButton);
        });

        updateView();

        return view;
    }

    private void launchLearningApp(Button button) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (ConnectManager.isAppInstalled(job.getLearnAppInfo().getAppId())) {
            ConnectManager.launchApp(getContext(), true, job.getLearnAppInfo().getAppId());
            getActivity().finish();
        } else {
            String title = getString(R.string.connect_downloading_learn);
            Navigation.findNavController(button).navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectDownloadingFragment(title, true));
        }
    }

    private void launchDeliveryApp(Button button) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (ConnectManager.isAppInstalled(job.getDeliveryAppInfo().getAppId())) {
            ConnectManager.launchApp(getContext(), false, job.getDeliveryAppInfo().getAppId());
            getActivity().finish();
        } else {
            String title = getString(R.string.connect_downloading_delivery);
            Navigation.findNavController(button).navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectDownloadingFragment(title, false));
        }
    }

    public void updateView() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (job == null || view == null) {
            return;
        }

        int completed = job.getCompletedVisits();
        int total = job.getMaxVisits();
        int percent = total > 0 ? (100 * completed / total) : 100;

        CircleProgressBar progress = view.findViewById(R.id.connect_progress_progress_bar);
        progress.setStrokeWidth(15);
        progress.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.connect_blackist_dark_blue_color));
        progress.setProgressColor(ContextCompat.getColor(getContext(), R.color.connect_aquva));
        progress.setProgress(percent);
//        progress.setMax(100);

        ConnectMediumTextView textView = view.findViewById(R.id.connect_progress_progress_text);
        textView.setText(String.format(Locale.getDefault(), "%d%%", percent));

        launchButton.setEnabled(!job.getIsUserSuspended());

        textView = view.findViewById(R.id.connect_progress_status_text);
        String completedText = getString(R.string.connect_progress_status, completed, total);
        if (job.isMultiPayment() && completed > 0) {
            //Get counts for each type
            Hashtable<String, Integer> paymentCounts = job.getDeliveryCountsPerPaymentUnit(false);

            //Add a line for each payment unit
            for (int unitIndex = 0; unitIndex < job.getPaymentUnits().size(); unitIndex++) {
                ConnectPaymentUnitRecord unit = job.getPaymentUnits().get(unitIndex);
                int count = 0;
                String stringKey = Integer.toString(unit.getUnitId());
                if (paymentCounts.containsKey(stringKey)) {
                    count = paymentCounts.get(stringKey);
                }

                completedText = String.format(Locale.getDefault(), "%s\n%s: %d", completedText, unit.getName(), count);
            }
        }

        textView.setText(completedText);

        int totalVisitCount = job.getDeliveries().size();
        int dailyVisitCount = job.numberOfDeliveriesToday();
        boolean finished = job.isFinished();

        String warningText = null;
        if (finished) {
            warningText = getString(R.string.connect_progress_warning_ended);
        } else if (job.getProjectStartDate().after(new Date())) {
            warningText = getString(R.string.connect_progress_warning_not_started);
        } else if (job.isMultiPayment()) {
            List<String> warnings = new ArrayList<>();
            Hashtable<String, Integer> totalPaymentCounts = job.getDeliveryCountsPerPaymentUnit(false);
            Hashtable<String, Integer> todayPaymentCounts = job.getDeliveryCountsPerPaymentUnit(true);
            for (int i = 0; i < job.getPaymentUnits().size(); i++) {
                ConnectPaymentUnitRecord unit = job.getPaymentUnits().get(i);
                String stringKey = Integer.toString(unit.getUnitId());

                int totalCount = 0;
                if (totalPaymentCounts.containsKey(stringKey)) {
                    totalCount = totalPaymentCounts.get(stringKey);
                }

                if (totalCount >= unit.getMaxTotal()) {
                    //Reached max total for this type
                    warnings.add(getString(R.string.connect_progress_warning_max_reached_multi, unit.getName()));
                } else {
                    int todayCount = 0;
                    if (todayPaymentCounts.containsKey(stringKey)) {
                        todayCount = todayPaymentCounts.get(stringKey);
                    }

                    if (todayCount >= unit.getMaxDaily()) {
                        //Reached daily max for this type
                        warnings.add(getString(R.string.connect_progress_warning_daily_max_reached_multi,
                                unit.getName()));
                    }
                }
            }

            if (warnings.size() > 0) {
                warningText = String.join("\n", warnings);
            }
        } else {
            if (totalVisitCount >= job.getMaxVisits()) {
                warningText = getString(R.string.connect_progress_warning_max_reached_single);
            } else if (dailyVisitCount >= job.getMaxDailyVisits()) {
                warningText = getString(R.string.connect_progress_warning_daily_max_reached_single);
            }
        }

        textView = view.findViewById(R.id.connect_progress_delivery_warning_text);
        textView.setVisibility(warningText != null ? View.VISIBLE : View.GONE);
        if (warningText != null) {
            textView.setText(warningText);
        }

        textView = view.findViewById(R.id.connect_progress_complete_by_text);
        String endText = ConnectManager.formatDate(job.getProjectEndDate());
        String text;
        if (finished) {
            //Project ended
            text = getString(R.string.connect_progress_ended, endText);
        } else if (job.getProjectStartDate() != null && job.getProjectStartDate().after(new Date())) {
            //Project hasn't started yet
            text = getString(R.string.connect_progress_begin_date, ConnectManager.formatDate(job.getProjectStartDate()), endText);
        } else if (job.getIsUserSuspended()) {
            text = getString(R.string.user_suspended);
        } else {
            text = getString(R.string.connect_progress_complete_by, endText);
        }
        textView.setText(text);
        int color = job.getIsUserSuspended() ? R.color.red : R.color.black;
        textView.setTextColor(getResources().getColor(color));
    }
}
