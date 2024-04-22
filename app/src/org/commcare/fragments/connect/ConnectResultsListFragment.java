package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.commcare.activities.connect.ConnectManager;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ConnectResultsListFragment extends Fragment {
    private ResultsAdapter adapter;
    private ConnectJobRecord job;
    public ConnectResultsListFragment() {
        // Required empty public constructor
    }

    public static ConnectResultsListFragment newInstance(ConnectJobRecord job) {
        ConnectResultsListFragment fragment = new ConnectResultsListFragment();
        fragment.job = job;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ConnectResultsListFragmentArgs args = ConnectResultsListFragmentArgs.fromBundle(getArguments());
        job = args.getJob();
        boolean showPayments = args.getShowPayments();
        getActivity().setTitle(job.getTitle());

        View view = inflater.inflate(R.layout.fragment_connect_results_list, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.results_list);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        adapter = new ResultsAdapter(job, showPayments);
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        return view;
    }

    public void updateView() {
        adapter.notifyDataSetChanged();
    }

    private static class ResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final ConnectJobRecord job;
        private final boolean showPayments;
        private Context parentContext;
        public ResultsAdapter(ConnectJobRecord job, boolean showPayments) {
            this.job = job;
            this.showPayments = showPayments;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            parentContext = parent.getContext();
            if(showPayments) {
                return new PaymentViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.connect_payment_item, parent, false));
            } else {
                return new VerificationViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.connect_verification_item, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if(holder instanceof VerificationViewHolder verificationHolder) {
                ConnectJobDeliveryRecord delivery = job.getDeliveries().get(position);

                verificationHolder.nameText.setText(delivery.getEntityName());
                verificationHolder.dateText.setText(ConnectManager.formatDate(delivery.getDate()));
                verificationHolder.statusText.setText(delivery.getStatus());
                verificationHolder.reasonText.setText(delivery.getReason());
            } else if(holder instanceof PaymentViewHolder paymentHolder) {
                final ConnectJobPaymentRecord payment = job.getPayments().get(position);

                String money = job.getMoneyString(Integer.parseInt(payment.getAmount()));
                paymentHolder.nameText.setText(parentContext.getString(R.string.connect_results_payment_description, money));

                paymentHolder.dateText.setText(parentContext.getString(R.string.connect_results_payment_date, ConnectManager.formatDate(payment.getDate())));

                boolean enabled = paymentHolder.updateConfirmedText(parentContext, payment);

                if(enabled) {
                    paymentHolder.confirmText.setOnClickListener(v -> {
                        ConnectManager.updatePaymentConfirmed(parentContext, payment, !payment.getConfirmed(), success -> {
                            paymentHolder.updateConfirmedText(parentContext, payment);
                        });
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            return showPayments ? job.getPayments().size() : job.getDeliveries().size();
        }

        public static class VerificationViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView dateText;
            TextView statusText;
            TextView reasonText;

            public VerificationViewHolder(@NonNull View itemView) {
                super(itemView);

                nameText = itemView.findViewById(R.id.delivery_item_name);
                dateText = itemView.findViewById(R.id.delivery_item_date);
                statusText = itemView.findViewById(R.id.delivery_item_status);
                reasonText = itemView.findViewById(R.id.delivery_item_reason);
            }
        }

        public static class PaymentViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView dateText;
            TextView confirmText;

            public PaymentViewHolder(@NonNull View itemView) {
                super(itemView);

                nameText = itemView.findViewById(R.id.name);
                dateText = itemView.findViewById(R.id.date);
                confirmText = itemView.findViewById(R.id.confirm);
            }

            public boolean updateConfirmedText(Context context, ConnectJobPaymentRecord payment) {
                boolean enabled;
                int confirmTextId;
                if(payment.getConfirmed()) {
                    enabled = payment.allowConfirmUndo();
                    confirmTextId = enabled ?
                            R.string.connect_results_payment_confirm_undo :
                            R.string.connect_results_payment_confirmed;
                } else {
                    enabled = payment.allowConfirm();
                    confirmTextId = enabled ?
                            R.string.connect_results_payment_confirm :
                            R.string.connect_results_payment_not_confirmed;
                }

                confirmText.setText(confirmTextId);
                confirmText.setTextColor(context.getResources().getColor(enabled ? R.color.blue : R.color.dark_grey));

                return enabled;
            }
        }
    }
}
