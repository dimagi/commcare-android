package org.commcare.fragments.connect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class ConnectResultsSummaryListFragment extends Fragment {
    private TextView singleDeliveryLabel;
    private ConstraintLayout multiPaymentContainer;
    private TextView earnedColumn;
    private TextView approvedColumn;
    private TextView rejectedColumn;
    private TextView pendingColumn;
    private TextView nameColumn;
    private Button deliveriesButton;
    private TextView earnedAmount;
    private TextView transferredAmount;
    private Button paymentsButton;

    public ConnectResultsSummaryListFragment() {
        // Required empty public constructor
    }

    public static ConnectResultsSummaryListFragment newInstance() {
        return new ConnectResultsSummaryListFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connect_results_summary_list, container, false);

        singleDeliveryLabel = view.findViewById(R.id.single_status_label);
        multiPaymentContainer = view.findViewById(R.id.multi_status_container);
        earnedColumn = view.findViewById(R.id.delivery_column_earned);
        approvedColumn = view.findViewById(R.id.delivery_column_approved);
        rejectedColumn = view.findViewById(R.id.delivery_column_rejected);
        pendingColumn = view.findViewById(R.id.delivery_column_pending);
        nameColumn = view.findViewById(R.id.delivery_column_type);
        deliveriesButton = view.findViewById(R.id.deliveries_button);
        earnedAmount = view.findViewById(R.id.payment_earned_amount);
        transferredAmount = view.findViewById(R.id.payment_transferred_amount);
        paymentsButton = view.findViewById(R.id.payments_button);

        deliveriesButton.setOnClickListener(v -> {
            Navigation.findNavController(deliveriesButton).navigate(ConnectDeliveryProgressFragmentDirections
                    .actionConnectJobDeliveryProgressFragmentToConnectResultsFragment(false));
        });

        paymentsButton.setOnClickListener(v -> {
            Navigation.findNavController(paymentsButton).navigate(ConnectDeliveryProgressFragmentDirections
                    .actionConnectJobDeliveryProgressFragmentToConnectResultsFragment(true));
        });

        updateView();

        return view;
    }

    public void updateView() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (job != null) {
            //Verification Status
            singleDeliveryLabel.setVisibility(job.isMultiPayment() ? View.GONE : View.VISIBLE);
            multiPaymentContainer.setVisibility(job.isMultiPayment() ? View.VISIBLE : View.GONE);
            if (job.isMultiPayment()) {
                //Get counts for all cells (by type and status)
                HashMap<String, HashMap<String, Integer>> paymentTypeAndStatusCounts = new HashMap<>();
                for(int i=0; i<job.getDeliveries().size(); i++) {
                    ConnectJobDeliveryRecord delivery = job.getDeliveries().get(i);

                    if(!paymentTypeAndStatusCounts.containsKey(delivery.getSlug())) {
                       paymentTypeAndStatusCounts.put(delivery.getSlug(), new HashMap<>());
                    }
                    HashMap<String, Integer> typeCounts = paymentTypeAndStatusCounts.get(delivery.getSlug());;

                    String status = delivery.getStatus();
                    int count = typeCounts.containsKey(status) ? typeCounts.get(status) :  0;
                    typeCounts.put(status, count + 1);
                }

                //Now populate the UI text
                List<String> nameLines = new ArrayList<>();
                List<String> pendingLines = new ArrayList<>();
                List<String> approvedLines = new ArrayList<>();
                List<String> rejectedLines = new ArrayList<>();
                List<String> earnedLines = new ArrayList<>();

                nameLines.add("");
                pendingLines.add(getString(R.string.connect_results_summary_pending));
                approvedLines.add(getString(R.string.connect_results_summary_approved));
                rejectedLines.add(getString(R.string.connect_results_summary_rejected));
                earnedLines.add(getString(R.string.connect_results_summary_earned));

                for(int i=0; i<job.getPaymentUnits().size(); i++) {
                    ConnectPaymentUnitRecord unit = job.getPaymentUnits().get(i);
                    HashMap<String, Integer> statusCounts;
                    String stringKey = Integer.toString(unit.getUnitId());
                    if(paymentTypeAndStatusCounts.containsKey(stringKey)) {
                        statusCounts = paymentTypeAndStatusCounts.get(stringKey);
                    } else {
                        statusCounts = new HashMap<>();
                    }

                    //Blank line
                    nameLines.add("");
                    pendingLines.add("");
                    approvedLines.add("");
                    rejectedLines.add("");
                    earnedLines.add("");

                    //Name line (the rest blank)
                    nameLines.add(unit.getName());
                    pendingLines.add("");
                    approvedLines.add("");
                    rejectedLines.add("");
                    earnedLines.add("");

                    //Counts line (name blank)
                    nameLines.add("");

                    String statusKey = "pending";
                    pendingLines.add(String.format(Locale.getDefault(), "%d",
                            statusCounts.containsKey(statusKey) ? statusCounts.get(statusKey) : 0));

                    statusKey = "approved";
                    int numApproved = statusCounts.containsKey(statusKey) ? statusCounts.get(statusKey) : 0;
                    approvedLines.add(String.format(Locale.getDefault(), "%d", numApproved));

                    statusKey = "rejected";
                    rejectedLines.add(String.format(Locale.getDefault(), "%d",
                            statusCounts.containsKey(statusKey) ? statusCounts.get(statusKey) : 0));
                    earnedLines.add(job.getMoneyString(numApproved * unit.getAmount()));
                }

                nameColumn.setText(String.join("\n", nameLines));
                pendingColumn.setText(String.join("\n", pendingLines));
                approvedColumn.setText(String.join("\n", approvedLines));
                rejectedColumn.setText(String.join("\n", rejectedLines));
                earnedColumn.setText(String.join("\n", earnedLines));
            } else {
                int numPending = 0;
                int numFailed = 0;
                int numApproved = 0;
                for (ConnectJobDeliveryRecord delivery : job.getDeliveries()) {
                    if (delivery.getStatus().equals("pending")) {
                        numPending++;
                    } else if (delivery.getStatus().equals("approved")) {
                        numApproved++;
                    } else {
                        numFailed++;
                    }
                }
                singleDeliveryLabel.setText(getString(R.string.connect_results_summary_verifications_description, numPending, numFailed, numApproved));
            }

            //Payment Status
            int total = 0;
            for (ConnectJobPaymentRecord payment : job.getPayments()) {
                try {
                    total += Integer.parseInt(payment.getAmount());
                } catch (Exception e) {
                    //Ignore at least for now
                }
            }

            earnedAmount.setText(job.getMoneyString(job.getPaymentAccrued()));
            transferredAmount.setText(job.getMoneyString(total));
        }
    }
}
