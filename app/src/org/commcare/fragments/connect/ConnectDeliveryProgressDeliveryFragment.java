package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.adapters.ConnectDeliveryProgressReportAdapter;
import org.commcare.android.database.connect.models.ConnectDeliveryDetails;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.views.connect.CircleProgressBar;
import org.commcare.views.connect.RoundedButton;
import org.commcare.views.connect.connecttextview.ConnectMediumTextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class ConnectDeliveryProgressDeliveryFragment extends Fragment {
    private View view;
    private boolean showLearningLaunch = true;
    private boolean showDeliveryLaunch = true;

    private RoundedButton launchButton;
    private RecyclerView recyclerView;
    private ConnectDeliveryProgressReportAdapter adapter;

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
            setDeliveriesData();
        });

        RoundedButton reviewButton = view.findViewById(R.id.connect_progress_review_button);
        reviewButton.setVisibility(showLearningLaunch ? View.VISIBLE : View.GONE);
        reviewButton.setOnClickListener(v -> {
            launchLearningApp(reviewButton);
        });

        updateView();
        setDeliveriesData();
        return view;
    }

    private void launchLearningApp(Button button) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (ConnectManager.isAppInstalled(job.getLearnAppInfo().getAppId())) {
            ConnectManager.launchApp(getActivity(), true, job.getLearnAppInfo().getAppId());
        } else {
            String title = getString(R.string.connect_downloading_learn);
            Navigation.findNavController(button).navigate(ConnectDeliveryProgressFragmentDirections.actionConnectJobDeliveryProgressFragmentToConnectDownloadingFragment(title, true));
        }
    }

    private void launchDeliveryApp(Button button) {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (ConnectManager.isAppInstalled(job.getDeliveryAppInfo().getAppId())) {
            ConnectManager.launchApp(getActivity(), false, job.getDeliveryAppInfo().getAppId());
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
    }

    public void setDeliveriesData() {
        ConnectDeliveryDetails connectDeliveryDetails = null;
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (job != null) {
            List<ConnectDeliveryDetails> deliveryProgressList = new ArrayList<>();
            HashMap<String, HashMap<String, Integer>> paymentTypeAndStatusCounts = new HashMap<>();
            int totalApproved = 0;
            int totalPending = 0;
            String totalAmount;
            long daysRemaining;
            ConnectJobDeliveryRecord delivery = null;

            if (!job.getDeliveries().isEmpty()) {
                // Loop through each delivery and count statuses
                for (int i = 0; i < job.getDeliveries().size(); i++) {
                    delivery = job.getDeliveries().get(i);
                    if (delivery == null) {
                        continue;
                    }
                    String deliverySlug = delivery.getSlug();

                    if (!paymentTypeAndStatusCounts.containsKey(deliverySlug)) {
                        paymentTypeAndStatusCounts.put(deliverySlug, new HashMap<>());
                    }

                    HashMap<String, Integer> typeCounts = paymentTypeAndStatusCounts.get(deliverySlug);
                    String status = delivery.getStatus();

                    int count = typeCounts.containsKey(status) ? typeCounts.get(status) : 0;
                    typeCounts.put(status, count + 1);
                }

                // Loop through the payment units and process the counts
                for (ConnectPaymentUnitRecord unit : job.getPaymentUnits()) {
                    if (unit == null) {
                        continue;
                    }

                    String unitIdKey = Integer.toString(unit.getUnitId());
                    HashMap<String, Integer> statusCounts = paymentTypeAndStatusCounts.containsKey(unitIdKey) ? paymentTypeAndStatusCounts.get(unitIdKey) : new HashMap<>();

                    // Get pending and approved counts
                    totalPending = statusCounts.containsKey("pending") ? statusCounts.get("pending") : 0;
                    totalApproved = statusCounts.containsKey("approved") ? statusCounts.get("approved") : 0;

                    // Calculate the total amount for this delivery (numApproved * unit amount)
                    totalAmount = job.getMoneyString(totalApproved * unit.getAmount());

                    // Calculate remaining days for the delivery
                    daysRemaining = calculateDaysPending(delivery);

                    int totalStatus = totalPending + totalApproved;
                    double approvedPercentage = totalStatus > 0 ? (double) totalApproved / totalStatus * 100 : 0.0;
                    connectDeliveryDetails = new ConnectDeliveryDetails();
                    connectDeliveryDetails.setUnitId(unit.getUnitId());
                    connectDeliveryDetails.setDeliveryName(unit.getName());
                    connectDeliveryDetails.setApprovedCount(totalApproved);
                    connectDeliveryDetails.setPendingCount(totalPending);
                    connectDeliveryDetails.setRemainingDays(daysRemaining);
                    connectDeliveryDetails.setTotalAmount(totalAmount);
                    connectDeliveryDetails.setApprovedPercentage(approvedPercentage);
                    deliveryProgressList.add(connectDeliveryDetails);
                }

                recyclerView = view.findViewById(R.id.rvDeliveryProgressReport);
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                adapter = new ConnectDeliveryProgressReportAdapter(getContext(), deliveryProgressList, unitName -> {
                    Navigation.findNavController(recyclerView).navigate(ConnectDeliveryProgressFragmentDirections
                            .actionConnectJobDeliveryProgressFragmentToConnectDeliveryFragment(unitName));
                });
                recyclerView.setAdapter(adapter);
            }
        }
    }

    private long calculateDaysPending(ConnectJobDeliveryRecord delivery) {
        Date dueDate = delivery.getDate();
        if (dueDate == null) {
            return 0;
        }
        long currentTime = System.currentTimeMillis();
        long dueTime = dueDate.getTime();
        long timeDifference = dueTime - currentTime;
        long daysPending = TimeUnit.MILLISECONDS.toDays(timeDifference);
        return Math.max(0, daysPending);
    }
}
