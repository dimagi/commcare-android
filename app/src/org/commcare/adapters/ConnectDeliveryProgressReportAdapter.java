package org.commcare.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectDeliveryDetails;
import org.commcare.dalvik.R;
import org.commcare.dalvik.databinding.ConnectDeliveryProgressItemBinding;

import java.util.List;

public class ConnectDeliveryProgressReportAdapter extends RecyclerView.Adapter<ConnectDeliveryProgressReportAdapter.ProgressBarViewHolder> {

    private Context context;
    List<ConnectDeliveryDetails> deliveryProgressList;
    DeliveryItemOnClickListener deliveryItemOnClickListener;

    public ConnectDeliveryProgressReportAdapter(Context context, List<ConnectDeliveryDetails> deliveryProgressList, DeliveryItemOnClickListener deliveryItemOnClickListener) {
        this.context = context;
        this.deliveryProgressList = deliveryProgressList;
        this.deliveryItemOnClickListener = deliveryItemOnClickListener;
    }

    @NonNull
    @Override
    public ProgressBarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ConnectDeliveryProgressItemBinding binding = ConnectDeliveryProgressItemBinding.inflate(
                LayoutInflater.from(context), parent, false);
        return new ProgressBarViewHolder(binding);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ProgressBarViewHolder holder, int position) {
        ConnectDeliveryDetails connectDeliveryDetails = deliveryProgressList.get(position);
        holder.binding.linearProgressBar.setProgress(Float.parseFloat(String.valueOf(connectDeliveryDetails.getApprovedPercentage())));
        holder.binding.linearProgressBar.setProgressColor(ContextCompat.getColor(context, R.color.connect_green));
        holder.binding.tvDeliveryTitle.setText(String.valueOf(connectDeliveryDetails.getDeliveryName()));
        holder.binding.tvApproved.setText(String.valueOf(connectDeliveryDetails.getApprovedCount()));
        holder.binding.tvDeliveryTotalAmount.setText(String.valueOf(connectDeliveryDetails.getTotalAmount()));
        holder.binding.tvRemaining.setText(connectDeliveryDetails.getPendingCount() + " in " + connectDeliveryDetails.getRemainingDays() + " days");
        holder.binding.imgArrowForward.setOnClickListener(view -> {
            deliveryItemOnClickListener.onClick(connectDeliveryDetails.getDeliveryName());
        });
    }

    @Override
    public int getItemCount() {
        return deliveryProgressList.size();
    }

    static class ProgressBarViewHolder extends RecyclerView.ViewHolder {
        ConnectDeliveryProgressItemBinding binding;

        public ProgressBarViewHolder(@NonNull ConnectDeliveryProgressItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

   public interface DeliveryItemOnClickListener {
        void onClick(String initName);
    }
}
