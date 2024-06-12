package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.android.database.connect.models.ConnectPaymentUnitRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
        earnedColumn = view.findViewById(R.id.delivery_column_earned);        //"@string/connect_results_summary_earned"
        approvedColumn = view.findViewById(R.id.delivery_column_approved);       //"@string/connect_results_summary_approved"/>
        rejectedColumn = view.findViewById(R.id.delivery_column_rejected);      //"@string/connect_results_summary_rejected"/>
        pendingColumn = view.findViewById(R.id.delivery_column_pending);       //"@string/connect_results_summary_pending"/>
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
                String nameText = "\n";
                String pendingText = "\n" + getString(R.string.connect_results_summary_pending);
                String approvedText = "\n" + getString(R.string.connect_results_summary_approved);
                String rejectedText = getString(R.string.connect_results_summary_rejected) + "\n";
                String earnedText = getString(R.string.connect_results_summary_earned) + "\n";
                for(int i=0; i<job.getPaymentUnits().size(); i++) {
                    ConnectPaymentUnitRecord unit = job.getPaymentUnits().get(i);
                    HashMap<String, Integer> statusCounts;
                    String stringKey = Integer.toString(unit.getUnitId());
                    if(paymentTypeAndStatusCounts.containsKey(stringKey)) {
                        statusCounts = paymentTypeAndStatusCounts.get(stringKey);
                    } else {
                        statusCounts = new HashMap<>();
                    }

                    nameText = String.format("%s\n\n%s", nameText, unit.getName());
                    String statusKey = "pending";
                    pendingText = String.format(Locale.getDefault(), "%s\n\n%d", pendingText,
                            statusCounts.containsKey(statusKey) ? statusCounts.get(statusKey) : 0);
                    statusKey = "approved";
                    int numApproved = statusCounts.containsKey(statusKey) ? statusCounts.get(statusKey) : 0;
                    approvedText = String.format(Locale.getDefault(), "%s\n\n%d", approvedText, numApproved);
                    statusKey = "rejected";
                    rejectedText = String.format(Locale.getDefault(), "%s\n\n%d", rejectedText,
                            statusCounts.containsKey(statusKey) ? statusCounts.get(statusKey) : 0);

                    earnedText = String.format("%s\n\n%s", earnedText, job.getMoneyString(numApproved * unit.getAmount()));
                }

                nameColumn.setText(nameText);
                pendingColumn.setText(pendingText);
                approvedColumn.setText(approvedText);
                rejectedColumn.setText(rejectedText);
                earnedColumn.setText(earnedText);
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
