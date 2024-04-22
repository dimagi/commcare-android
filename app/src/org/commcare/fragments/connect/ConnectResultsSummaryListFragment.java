package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.commcare.activities.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConnectResultsSummaryListFragment extends Fragment {
    private VerificationSummaryListAdapter adapter;
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

        RecyclerView recyclerView = view.findViewById(R.id.summary_list);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        adapter = new VerificationSummaryListAdapter();
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        return view;
    }

    public void updateView() {
        adapter.notifyDataSetChanged();
    }

    private static class VerificationSummaryListAdapter extends RecyclerView.Adapter<VerificationSummaryListAdapter.VerificationSummaryItemViewHolder> {
        private Context parentContext;

        public VerificationSummaryListAdapter() {

        }

        @NonNull
        @Override
        public VerificationSummaryItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            parentContext = parent.getContext();
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.connect_results_summary_item, parent, false);

            return new VerificationSummaryItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VerificationSummaryItemViewHolder holder, int position) {
            ConnectJobRecord job = ConnectManager.getActiveJob();
            holder.titleText.setText(parentContext.getString(position == 0 ?
                    R.string.connect_results_summary_verifications_title :
                    R.string.connect_results_summary_payments_title));

            String description = "";
            if(job != null) {
                if (position == 0) {
                    //Verification Status
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
                    description = parentContext.getString(R.string.connect_results_summary_verifications_description, numPending, numFailed, numApproved);
                } else {
                    //Payment Status
                    int total = 0;
                    for (ConnectJobPaymentRecord payment : job.getPayments()) {
                        try {
                            total += Integer.parseInt(payment.getAmount());
                        } catch(Exception e) {
                            //Ignore at least for now
                        }
                    }

                    String accrued = job.getMoneyString(job.getPaymentAccrued());
                    String paid = job.getMoneyString(total);
                    description = parentContext.getString(R.string.connect_results_summary_payments_description, accrued, paid);
                }
            }

            holder.descriptionText.setText(description);

            holder.button.setOnClickListener(v -> {
                Navigation.findNavController(holder.button).navigate(ConnectDeliveryProgressFragmentDirections
                        .actionConnectJobDeliveryProgressFragmentToConnectResultsFragment(position > 0));
            });
        }

        @Override
        public int getItemCount() {
            return 2;
        }

        public static class VerificationSummaryItemViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            TextView descriptionText;
            ImageView button;

            public VerificationSummaryItemViewHolder(@NonNull View itemView) {
                super(itemView);

                titleText = itemView.findViewById(R.id.title_label);
                descriptionText = itemView.findViewById(R.id.description_label);
                button = itemView.findViewById(R.id.button);
            }
        }
    }
}
