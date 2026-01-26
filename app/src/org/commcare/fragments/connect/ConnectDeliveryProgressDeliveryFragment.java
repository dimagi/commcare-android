package org.commcare.fragments.connect;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.RequiresApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.adapters.ConnectDeliveryProgressReportAdapter;
import org.commcare.android.database.connect.models.ConnectDeliveryDetails;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectProgressDeliveryBinding;
import org.commcare.views.connect.CircleProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ConnectDeliveryProgressDeliveryFragment extends ConnectJobFragment<FragmentConnectProgressDeliveryBinding> {
    private RecyclerView recyclerView;
    private ConnectDeliveryProgressReportAdapter adapter;

    public static ConnectDeliveryProgressDeliveryFragment newInstance() {
        return new ConnectDeliveryProgressDeliveryFragment();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public @NotNull View onCreateView(@NotNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        getBinding().btnSync.setOnClickListener(v -> {
            ConnectDeliveryProgressFragment parentFragment = (ConnectDeliveryProgressFragment)getParentFragment();
            if (parentFragment != null) {
                parentFragment.refresh();
            }
            populateDeliveryProgress();
        });

        updateProgressSummary();
        populateDeliveryProgress();
        return view;
    }

    public void updateProgressSummary() {
        int completed = job.getCompletedVisits();
        int total = job.getMaxVisits();
        int percent = total > 0 ? (100 * completed / total) : 100;

        CircleProgressBar progress = getBinding().connectProgressProgressBar;
        progress.setProgress(percent);
        getBinding().connectProgressProgressText.setText(String.format(Locale.getDefault(), "%d%%", percent));

        StringBuilder completedText = new StringBuilder(
                getString(R.string.connect_progress_status, completed, total));

        if (job.isMultiPayment() && completed > 0) {
            HashMap<String, Integer> paymentCounts = job.getDeliveryCountsPerPaymentUnit(false);

            for (ConnectPaymentUnitRecord unit : job.getPaymentUnits()) {
                String key = String.valueOf(unit.getUnitId());
                int count = paymentCounts.containsKey(key) ? paymentCounts.get(key) : 0;
                completedText.append(String.format(Locale.getDefault(), "\n%s: %d", unit.getName(), count));
            }
        }

        getBinding().connectProgressStatusText.setText(completedText.toString());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void populateDeliveryProgress() {
        if (job.getDeliveries().isEmpty()) {
            return;
        }

        List<ConnectDeliveryDetails> deliveryProgressList = new ArrayList<>();
        HashMap<String, HashMap<String, Integer>> statusMap = getStatusMap(job);

        for (ConnectPaymentUnitRecord unit : job.getPaymentUnits()) {
            String unitIdKey = String.valueOf(unit.getUnitId());
            HashMap<String, Integer> statusCounts = statusMap.containsKey(unitIdKey) ? statusMap.get(unitIdKey)
                    : new HashMap<>();
            int approved = statusCounts.getOrDefault("approved", 0);
            int remaining = unit.getMaxTotal() - approved;
            String amount = job.getMoneyString(approved * unit.getAmount());
            long daysLeft = job.getDaysRemaining();
            double percentApproved = unit.getMaxTotal() > 0 ? (double)approved / unit.getMaxTotal() * 100 : 0.0;

            deliveryProgressList.add(new ConnectDeliveryDetails(
                    unit.getUnitId(), unit.getName(), approved, remaining, amount, daysLeft, percentApproved
            ));
        }

        recyclerView = getBinding().rvDeliveryProgressReport;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        if (adapter == null) {
            adapter = new ConnectDeliveryProgressReportAdapter(
                    getContext(), deliveryProgressList, unitName -> Navigation.findNavController(recyclerView)
                    .navigate(ConnectDeliveryProgressFragmentDirections
                            .actionConnectJobDeliveryProgressFragmentToConnectDeliveryFragment(unitName))
            );
            recyclerView.setAdapter(adapter);
        } else {
            recyclerView.setAdapter(adapter);
            adapter.updateData(deliveryProgressList);
        }
    }

    private HashMap<String, HashMap<String, Integer>> getStatusMap(ConnectJobRecord job) {
        HashMap<String, HashMap<String, Integer>> statusMap = new HashMap<>();

        for (ConnectJobDeliveryRecord delivery : job.getDeliveries()) {
            if (delivery == null) continue;

            String slug = delivery.getSlug();
            HashMap<String, Integer> countMap;

            if (statusMap.containsKey(slug)) {
                countMap = statusMap.get(slug);
            } else {
                countMap = new HashMap<>();
                statusMap.put(slug, countMap);
            }

            String status = delivery.getStatus();
            int count = countMap.containsKey(status) ? countMap.get(status) : 0;
            countMap.put(status, count + 1);
        }

        return statusMap;
    }


    /**
     * This was added to re-calculate the correct height of a fragment in ViewPager2. This is required as ViewPager2
     * has bug currently due to which it's not calculating the height of it's different fragment correctly.
     * When found some other solution for this ViewPager2 bug, we can remove this but make sure to QA
     * by pressing sync button while in different fragments.
     */
    @Override
    public void onResume() {
        super.onResume();
        getBinding().getRoot().requestLayout();
    }

    @Override
    protected @NotNull FragmentConnectProgressDeliveryBinding inflateBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return FragmentConnectProgressDeliveryBinding.inflate(inflater, container, false);
    }
}
