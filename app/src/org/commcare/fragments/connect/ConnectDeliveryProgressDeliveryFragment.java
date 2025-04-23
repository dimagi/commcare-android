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

    private RecyclerView recyclerView;

    public ConnectDeliveryProgressDeliveryFragment() {
        // Required empty public constructor
    }

    public static ConnectDeliveryProgressDeliveryFragment newInstance() {
        return new ConnectDeliveryProgressDeliveryFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_connect_progress_delivery, container, false);

        Button btnSync = view.findViewById(R.id.btnSync);
        btnSync.setOnClickListener(view -> {
            ConnectDeliveryProgressFragment parentFragment = (ConnectDeliveryProgressFragment) getParentFragment();
            if (parentFragment != null) {
                parentFragment.refreshData();
            }
            setDeliveriesData();
        });

        updateView();
        setDeliveriesData();
        return view;
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
        progress.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.connect_blackist_dark_blue_color));
        progress.setProgressColor(ContextCompat.getColor(requireContext(), R.color.connect_aquva));
        progress.setProgress(percent);

        ConnectMediumTextView textView = view.findViewById(R.id.connect_progress_progress_text);
        textView.setText(String.format(Locale.getDefault(), "%d%%", percent));

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
        ConnectDeliveryDetails connectDeliveryDetails;
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (job != null) {
            List<ConnectDeliveryDetails> deliveryProgressList = new ArrayList<>();
            HashMap<String, HashMap<String, Integer>> paymentTypeAndStatusCounts = new HashMap<>();

            if (!job.getDeliveries().isEmpty()) {
                // Loop through each delivery and count statuses
                for (int i = 0; i < job.getDeliveries().size(); i++) {
                    ConnectJobDeliveryRecord delivery = job.getDeliveries().get(i);
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
                    int totalApproved = statusCounts.containsKey("approved") ? statusCounts.get("approved") : 0;
                    int remaining = unit.getMaxTotal() - totalApproved;

                    // Calculate the total amount for this delivery (numApproved * unit amount)
                    String totalAmount = job.getMoneyString(totalApproved * unit.getAmount());

                    // Calculate remaining days for the delivery
                    long daysRemaining = job.getDaysRemaining();

                    double approvedPercentage = unit.getMaxTotal() > 0 ? (double) totalApproved / unit.getMaxTotal() * 100 : 0.0;
                    connectDeliveryDetails = new ConnectDeliveryDetails();
                    connectDeliveryDetails.setUnitId(unit.getUnitId());
                    connectDeliveryDetails.setDeliveryName(unit.getName());
                    connectDeliveryDetails.setApprovedCount(totalApproved);
                    connectDeliveryDetails.setPendingCount(remaining);
                    connectDeliveryDetails.setRemainingDays(daysRemaining);
                    connectDeliveryDetails.setTotalAmount(totalAmount);
                    connectDeliveryDetails.setApprovedPercentage(approvedPercentage);
                    deliveryProgressList.add(connectDeliveryDetails);
                }

                recyclerView = view.findViewById(R.id.rvDeliveryProgressReport);
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                ConnectDeliveryProgressReportAdapter adapter = new ConnectDeliveryProgressReportAdapter(getContext(), deliveryProgressList, unitName -> {
                    Navigation.findNavController(recyclerView).navigate(ConnectDeliveryProgressFragmentDirections
                            .actionConnectJobDeliveryProgressFragmentToConnectDeliveryFragment(unitName));
                });
                recyclerView.setAdapter(adapter);
            }
        }
    }
}
