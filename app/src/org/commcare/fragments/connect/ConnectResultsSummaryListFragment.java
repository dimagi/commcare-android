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


public class ConnectResultsSummaryListFragment extends Fragment {
    private TextView earnedAmount;
    private TextView transferredAmount;

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

        earnedAmount = view.findViewById(R.id.payment_earned_amount);
        transferredAmount = view.findViewById(R.id.payment_transferred_amount);

        updateView();

        RecyclerView recyclerView = view.findViewById(R.id.results_list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);
        ResultsAdapter adapter = new ResultsAdapter(true);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));

        return view;
    }

    public void updateView() {
        ConnectJobRecord job = ConnectManager.getActiveJob();
        if (job != null) {
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
            if (showPayments) {
                return new PaymentViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.connect_payment_item, parent, false));
            } else {
                return new VerificationViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.connect_verification_item, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ConnectJobRecord job = ConnectManager.getActiveJob();
            if (holder instanceof VerificationViewHolder verificationHolder) {
                ConnectJobDeliveryRecord delivery = job.getDeliveries().get(position);

                verificationHolder.nameText.setText(delivery.getEntityName());
                verificationHolder.dateText.setText(ConnectManager.formatDate(delivery.getDate()));
                verificationHolder.statusText.setText(delivery.getStatus());
                verificationHolder.reasonText.setText(delivery.getReason());
            } else if (holder instanceof PaymentViewHolder paymentHolder) {
                final ConnectJobPaymentRecord payment = job.getPayments().get(position);

                String money = job.getMoneyString(Integer.parseInt(payment.getAmount()));
                paymentHolder.nameText.setText(job.getMoneyString(Integer.parseInt(payment.getAmount())));

                paymentHolder.dateText.setText(ConnectManager.paymentDateFormat(payment.getDate()));

                boolean enabled = paymentHolder.updateConfirmedText(parentContext, payment);

                if (enabled) {
                    setupPaymentAction(paymentHolder, payment, money, true,
                            R.drawable.ic_connect_payment_status_not_transferred,
                            R.string.connect_payment_confirm_transferred);

                    setupPaymentAction(paymentHolder, payment, money, false,
                            R.drawable.ic_connect_payment_status_transferred,
                            R.string.connect_payment_revoke_transferred);
                }
            }
        }

        private void setupPaymentAction(
                PaymentViewHolder paymentHolder,
                ConnectJobPaymentRecord payment,
                String money,
                boolean isConfirmation,
                int iconResId,
                int titleResId) {

            View.OnClickListener clickListener = v -> showDialog(
                    parentContext,
                    ContextCompat.getDrawable(parentContext, iconResId),
                    parentContext.getString(titleResId),
                    money,
                    ConnectManager.paymentDateFormat(payment.getDate()),
                    isConfirmation,
                    result -> ConnectManager.updatePaymentConfirmed(parentContext, payment, result, success -> {
                        paymentHolder.updateConfirmedText(parentContext, payment);
                    })
            );

            if (isConfirmation) {
                paymentHolder.confirmText.setOnClickListener(clickListener);
            } else {
                paymentHolder.llRevertPayment.setOnClickListener(clickListener);
            }
        }

        @Override
        public int getItemCount() {
            ConnectJobRecord job = ConnectManager.getActiveJob();
            return showPayments ? job.getPayments().size() : job.getDeliveries().size();
        }

        public static class VerificationViewHolder extends RecyclerView.ViewHolder {
            final TextView nameText;
            final TextView dateText;
            final TextView statusText;
            final TextView reasonText;

            public VerificationViewHolder(@NonNull View itemView) {
                super(itemView);

                nameText = itemView.findViewById(R.id.delivery_item_name);
                dateText = itemView.findViewById(R.id.delivery_item_date);
                statusText = itemView.findViewById(R.id.delivery_item_status);
                reasonText = itemView.findViewById(R.id.delivery_item_reason);
            }
        }

        public static class PaymentViewHolder extends RecyclerView.ViewHolder {
            final TextView nameText;
            final TextView dateText;
            final TextView tvPaymentStatus;
            final CardView confirmText;
            final ImageView imgPaymentStatusIcon;
            final ImageView imgReceived;
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
                    handleConfirmedState(context, payment);
                    return payment.allowConfirmUndo();
                } else {
                    handleUnconfirmedState(context, payment);
                    return payment.allowConfirm();
                }
            }

            private void handleConfirmedState(Context context, ConnectJobPaymentRecord payment) {
                imgReceived.setVisibility(View.VISIBLE);
                confirmText.setVisibility(View.GONE);
                llRevertPayment.setVisibility(View.VISIBLE);

                setPaymentStatus(context, R.string.connect_payment_received,
                        R.drawable.ic_connect_payment_status_transferred);
            }

            private void handleUnconfirmedState(Context context, ConnectJobPaymentRecord payment) {
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

        public void showDialog(
                Context context,
                Drawable statusIcon,
                String title,
                String amount,
                String date,
                boolean paymentStatus,
                OnDialogResultListener dialogResultListener
        ) {
            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            DialogPaymentConfirmationBinding binding = DialogPaymentConfirmationBinding.inflate(LayoutInflater.from(context));
            dialog.setContentView(binding.getRoot());

            // Set dialog to match_parent and add 10dp horizontal margin
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;

            // Convert 10dp to pixels
            int marginInDp = 10;
            float density = context.getResources().getDisplayMetrics().density;
            int marginInPx = (int) (marginInDp * density);

            layoutParams.horizontalMargin = marginInPx;
            dialog.getWindow().setAttributes(layoutParams);

            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().getDecorView().setPadding(marginInPx, 0, marginInPx, 0);

            binding.imgPaymentStatus.setImageDrawable(statusIcon);
            binding.tvPaymentConfirmationTitle.setText(title);
            binding.tvPaymentAmount.setText(amount);
            binding.tvPaymentDate.setText(date);

            binding.riYes.setOnClickListener(view -> {
                if (dialogResultListener != null) {
                    dialogResultListener.onYesClicked(paymentStatus);
                }
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
