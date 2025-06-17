package org.commcare.fragments.connect;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectJobDeliveryRecord;
import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectJobRecord;
import org.commcare.connect.ConnectManager;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.DialogPaymentConfirmationBinding;
import org.commcare.dalvik.databinding.FragmentConnectResultsSummaryListBinding;

public class ConnectResultsSummaryListFragment extends Fragment {
    private FragmentConnectResultsSummaryListBinding binding;
    private ResultsAdapter adapter;

    public static ConnectResultsSummaryListFragment newInstance() {
        return new ConnectResultsSummaryListFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentConnectResultsSummaryListBinding.inflate(inflater, container, false);
        setupRecyclerView();
        updateView();
        return binding.getRoot();
    }

    public void updateView() {
        updateSummaryView();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;        // prevent view-leak
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        binding.resultsList.setLayoutManager(layoutManager);
        adapter = new ResultsAdapter(true);
        binding.resultsList.setAdapter(adapter);
        binding.resultsList.addItemDecoration(
                new DividerItemDecoration(getContext(), layoutManager.getOrientation()));
    }

    private void updateSummaryView() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (job != null) {
            int total = 0;
            for (ConnectJobPaymentRecord payment : job.getPayments()) {
                try {
                    total += Integer.parseInt(payment.getAmount());
                } catch (Exception ignored) {
                }
            }
            binding.paymentEarnedAmount.setText(job.getMoneyString(job.getPaymentAccrued()));
            binding.paymentTransferredAmount.setText(job.getMoneyString(total));
        }
    }

    private static class ResultsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final boolean showPayments;
        private Context parentContext;

        public ResultsAdapter(boolean showPayments) {
            this.showPayments = showPayments;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            parentContext = parent.getContext();
            int layoutRes = showPayments ? R.layout.connect_payment_item : R.layout.connect_verification_item;
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            return showPayments ? new PaymentViewHolder(view) : new VerificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ConnectJobRecord job = ConnectManager.getActiveJob();
            if (holder instanceof VerificationViewHolder vh) {
                bindVerificationItem(vh, job.getDeliveries().get(position));
            } else if (holder instanceof PaymentViewHolder ph) {
                bindPaymentItem(ph, job.getPayments().get(position), job);
            }
        }

        private void bindVerificationItem(VerificationViewHolder holder, ConnectJobDeliveryRecord delivery) {
            holder.nameText.setText(delivery.getEntityName());
            holder.dateText.setText(ConnectManager.formatDate(delivery.getDate()));
            holder.statusText.setText(delivery.getStatus());
            holder.reasonText.setText(delivery.getReason());
        }

        private void bindPaymentItem(PaymentViewHolder holder, ConnectJobPaymentRecord payment,
                                     ConnectJobRecord job) {
            String amount = job.getMoneyString(Integer.parseInt(payment.getAmount()));
            holder.nameText.setText(amount);
            holder.dateText.setText(ConnectManager.paymentDateFormat(payment.getDate()));
            boolean enabled = holder.updateConfirmedText(parentContext, payment);

            if (enabled) {
                setupPaymentAction(holder, payment, amount, true,
                        R.drawable.ic_connect_payment_status_not_transferred,
                        R.string.connect_payment_confirm_transferred);
                setupPaymentAction(holder, payment, amount, false,
                        R.drawable.ic_connect_payment_status_transferred,
                        R.string.connect_payment_revoke_transferred);
            }
        }

        private void setupPaymentAction(PaymentViewHolder holder, ConnectJobPaymentRecord payment, String money,
                                        boolean isConfirmation, int iconResId, int titleResId) {
            View.OnClickListener listener = v -> showDialog(
                    parentContext,
                    ContextCompat.getDrawable(parentContext, iconResId),
                    parentContext.getString(titleResId),
                    money,
                    ConnectManager.paymentDateFormat(payment.getDate()),
                    isConfirmation,
                    result -> ConnectManager.updatePaymentConfirmed(parentContext, payment, result,
                            success -> holder.updateConfirmedText(parentContext, payment))
            );
            if (isConfirmation) {
                holder.confirmText.setOnClickListener(listener);
            } else {
                holder.llRevertPayment.setOnClickListener(listener);
            }
        }

        @Override
        public int getItemCount() {
            ConnectJobRecord job = ConnectManager.getActiveJob();
            return showPayments ? job.getPayments().size() : job.getDeliveries().size();
        }

        public static class VerificationViewHolder extends RecyclerView.ViewHolder {
            final TextView nameText, dateText, statusText, reasonText;

            public VerificationViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.delivery_item_name);
                dateText = itemView.findViewById(R.id.delivery_item_date);
                statusText = itemView.findViewById(R.id.delivery_item_status);
                reasonText = itemView.findViewById(R.id.delivery_item_reason);
            }
        }

        public static class PaymentViewHolder extends RecyclerView.ViewHolder {
            final TextView nameText, dateText, tvPaymentStatus;
            final CardView confirmText;
            final ImageView imgPaymentStatusIcon, imgReceived;
            final LinearLayout llRevertPayment;

            public PaymentViewHolder(@NonNull View itemView) {
                super(itemView);
                nameText = itemView.findViewById(R.id.name);
                dateText = itemView.findViewById(R.id.date);
                confirmText = itemView.findViewById(R.id.confirm);
                tvPaymentStatus = itemView.findViewById(R.id.tv_payment_status);
                imgPaymentStatusIcon = itemView.findViewById(R.id.imgPaymentStatusIcon);
                imgReceived = itemView.findViewById(R.id.imgReceived);
                llRevertPayment = itemView.findViewById(R.id.llRevertPayment);
            }

            public boolean updateConfirmedText(Context context, ConnectJobPaymentRecord payment) {
                if (payment.getConfirmed()) {
                    setConfirmedState(context);
                    return payment.allowConfirmUndo();
                } else {
                    setUnconfirmedState(context);
                    return payment.allowConfirm();
                }
            }

            private void setConfirmedState(Context context) {
                imgReceived.setVisibility(View.VISIBLE);
                confirmText.setVisibility(View.GONE);
                llRevertPayment.setVisibility(View.VISIBLE);
                setPaymentStatus(context, R.string.connect_payment_received,
                        R.drawable.ic_connect_payment_status_transferred);
            }

            private void setUnconfirmedState(Context context) {
                imgReceived.setVisibility(View.GONE);
                confirmText.setVisibility(View.VISIBLE);
                llRevertPayment.setVisibility(View.GONE);
                setPaymentStatus(context, R.string.connect_payment_transferred,
                        R.drawable.ic_connect_payment_status_not_transferred);
            }

            private void setPaymentStatus(Context context, int statusTextId, int iconResId) {
                tvPaymentStatus.setText(statusTextId);
                imgPaymentStatusIcon.setImageDrawable(ContextCompat.getDrawable(context, iconResId));
            }
        }

        public void showDialog(Context context, Drawable statusIcon, String title, String amount, String date,
                               boolean paymentStatus, OnDialogResultListener dialogResultListener) {
            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

            DialogPaymentConfirmationBinding binding = DialogPaymentConfirmationBinding.inflate(
                    LayoutInflater.from(context));
            dialog.setContentView(binding.getRoot());

            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;

            int marginInPx = (int)(10 * context.getResources().getDisplayMetrics().density);
            float marginFraction = marginInPx /
                            (float) context.getResources().getDisplayMetrics().widthPixels;
            layoutParams.horizontalMargin = marginFraction;
            dialog.getWindow().setAttributes(layoutParams);
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().getDecorView().setPadding(marginInPx, 0, marginInPx, 0);

            binding.imgPaymentStatus.setImageDrawable(statusIcon);
            binding.tvPaymentConfirmationTitle.setText(title);
            binding.tvPaymentAmount.setText(amount);
            binding.tvPaymentDate.setText(date);

            binding.riYes.setOnClickListener(view -> {
                if (dialogResultListener != null) dialogResultListener.onYesClicked(paymentStatus);
                dialog.dismiss();
            });

            binding.riNo.setOnClickListener(view -> dialog.dismiss());
            dialog.show();
        }
    }

    public interface OnDialogResultListener {
        void onYesClicked(boolean result);
    }
}
