package org.commcare.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.commcare.android.database.connect.models.ConnectDeliveryPaymentSummaryInfo;
import org.commcare.dalvik.R;
import org.commcare.views.connect.LinearProgressBar;

import java.util.List;

public class ConnectProgressJobSummaryAdapter extends RecyclerView.Adapter<ConnectProgressJobSummaryAdapter.ViewHolder> {

    private List<ConnectDeliveryPaymentSummaryInfo> primaryVisits;

    public ConnectProgressJobSummaryAdapter(List<ConnectDeliveryPaymentSummaryInfo> primaryVisits) {
        this.primaryVisits = primaryVisits;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_progress_job_summary_visit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConnectDeliveryPaymentSummaryInfo primaryVisit = primaryVisits.get(position);
        holder.tvPrimaryVisitTitle.setText(primaryVisit.getPaymentUnitName());
        holder.tvPrimaryVisitCount.setText(String.valueOf(primaryVisit.getPaymentUnitAmount()) + "/" + String.valueOf(primaryVisit.getPaymentUnitMaxDaily()));

        // Calculate the percentage (make sure to avoid division by zero)
        double percentage = 0;
        if (primaryVisit.getPaymentUnitMaxDaily() > 0) {
            percentage = ((double) primaryVisit.getPaymentUnitAmount() / primaryVisit.getPaymentUnitMaxDaily()) * 100;
        }
        holder.lpPrimaryVisitProgress.setProgress((float) percentage);
    }

    @Override
    public int getItemCount() {
        return primaryVisits.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPrimaryVisitTitle;
        TextView tvPrimaryVisitCount;
        LinearProgressBar lpPrimaryVisitProgress;

        ViewHolder(View itemView) {
            super(itemView);
            tvPrimaryVisitTitle = itemView.findViewById(R.id.tv_primary_visit_title);
            tvPrimaryVisitCount = itemView.findViewById(R.id.tv_primary_visit_count);
            lpPrimaryVisitProgress = itemView.findViewById(R.id.lp_primary_visit_progress);
        }
    }
}