package org.commcare.fragments.connect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.connect.ConnectDateUtils;
import org.commcare.connect.ConnectJobHelper;
import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.FragmentConnectResultsListBinding;

public class ConnectResultsListFragment extends ConnectJobFragment {
    private ResultsAdapter adapter;
    private FragmentConnectResultsListBinding binding;

    public static ConnectResultsListFragment newInstance() {
        return new ConnectResultsListFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectResultsListBinding.inflate(inflater, container, false);
        setupRecyclerView();
        return binding.getRoot();
    }

    private void setupRecyclerView() {
        ConnectResultsListFragmentArgs args = ConnectResultsListFragmentArgs.fromBundle(getArguments());
        boolean showPayments = args.getShowPayments();
        requireActivity().setTitle(job.getTitle());

        RecyclerView recyclerView = binding.resultsList;
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        adapter = new ResultsAdapter(showPayments, getContext());
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), layoutManager.getOrientation()));
    }

    public void updateView() {
        adapter.notifyDataSetChanged();
    }

    private static class ResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private ConnectJobRecord job;
        private final boolean showPayments;
        private final Context context;

        ResultsAdapter(boolean showPayments, Context context) {
            this.showPayments = showPayments;
            this.context = context;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(context);
            return showPayments ?
                    new PaymentViewHolder(inflater.inflate(R.layout.connect_payment_item, parent, false)) :
                    new VerificationViewHolder(
                            inflater.inflate(R.layout.connect_verification_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

            if (holder instanceof VerificationViewHolder verificationHolder) {
                bindVerificationItem(verificationHolder, job.getDeliveries().get(position));
            } else if (holder instanceof PaymentViewHolder paymentHolder) {
                bindPaymentItem(paymentHolder, job, job.getPayments().get(position));
            }
        }

        private void bindVerificationItem(VerificationViewHolder holder, ConnectJobDeliveryRecord delivery) {
            holder.nameText.setText(delivery.getEntityName());
            holder.dateText.setText(ConnectDateUtils.INSTANCE.formatDate(delivery.getDate()));
            holder.statusText.setText(delivery.getStatus());
            holder.reasonText.setText(delivery.getReason());
        }

        private void bindPaymentItem(PaymentViewHolder holder, ConnectJobRecord job,
                                     ConnectJobPaymentRecord payment) {
            String amount = job.getMoneyString(Integer.parseInt(payment.getAmount()));
            holder.nameText.setText(amount);
            holder.dateText.setText(ConnectDateUtils.INSTANCE.formatDate(payment.getDate()));

            boolean enabled = holder.updateConfirmedText(context, payment);
            if (enabled) {
                holder.confirmText.setOnClickListener(
                        enabled ? v -> ConnectJobHelper.INSTANCE.updatePaymentConfirmed(context, payment,
                                !payment.getConfirmed(), success -> holder.updateConfirmedText(context, payment))
                                : null);
            }
        }

        @Override
        public int getItemCount() {
            return showPayments ? job.getPayments().size() : job.getDeliveries().size();
        }

        static class VerificationViewHolder extends RecyclerView.ViewHolder {
            final TextView nameText, dateText, statusText, reasonText;

            VerificationViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.delivery_item_name);
                dateText = itemView.findViewById(R.id.delivery_item_date);
                statusText = itemView.findViewById(R.id.delivery_item_status);
                reasonText = itemView.findViewById(R.id.delivery_item_reason);
            }
        }

        static class PaymentViewHolder extends RecyclerView.ViewHolder {
            final TextView nameText, dateText, confirmText;

            PaymentViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.name);
                dateText = itemView.findViewById(R.id.date);
                confirmText = itemView.findViewById(R.id.confirm);
            }

            boolean updateConfirmedText(Context context, ConnectJobPaymentRecord payment) {
                boolean enabled;
                int textResId;

                if (payment.getConfirmed()) {
                    enabled = payment.allowConfirmUndo();
                    textResId = enabled ? R.string.connect_results_payment_confirm_undo
                            : R.string.connect_results_payment_confirmed;
                } else {
                    enabled = payment.allowConfirm();
                    textResId = enabled ? R.string.connect_results_payment_confirm
                            : R.string.connect_results_payment_not_confirmed;
                }

                confirmText.setText(textResId);
                confirmText.setTextColor(
                        context.getResources().getColor(enabled ? R.color.blue : R.color.dark_grey));

                return enabled;
            }
        }
    }
}
