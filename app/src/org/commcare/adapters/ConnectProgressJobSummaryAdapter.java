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
import java.util.Locale;

public class ConnectProgressJobSummaryAdapter extends RecyclerView.Adapter<ConnectProgressJobSummaryAdapter.ViewHolder> {

    private List<ConnectDeliveryPaymentSummaryInfo> deliverySummaries;

    public ConnectProgressJobSummaryAdapter(List<ConnectDeliveryPaymentSummaryInfo> deliverySummaries) {
        this.deliverySummaries = deliverySummaries;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_progress_job_summary_visit,
                parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConnectDeliveryPaymentSummaryInfo summary = deliverySummaries.get(position);
        holder.tvPrimaryVisitTitle.setText(summary.getPaymentUnitName());
        holder.tvPrimaryVisitCount.setText(String.format(Locale.getDefault(), "%d/%d",
                summary.getPaymentUnitAmount(), summary.getPaymentUnitMaxDaily()));

        float percentage = 0;
        if (summary.getPaymentUnitMaxDaily() > 0) {
            percentage = ((float) summary.getPaymentUnitAmount() / summary.getPaymentUnitMaxDaily()) * 100;
        }
        if (summary.getPaymentUnitAmount() >= summary.getPaymentUnitMaxDaily()){
            holder.lpPrimaryVisitProgress.setProgressColor(holder.lpPrimaryVisitProgress.getResources().getColor(R.color.green));
        }
        holder.lpPrimaryVisitProgress.setProgress(percentage);
    }

    @Override
    public int getItemCount() {
        return deliverySummaries.size();
    }

    public void setDeliverySummaries(List<ConnectDeliveryPaymentSummaryInfo> deliverySummaries) {
        this.deliverySummaries = deliverySummaries;
        notifyDataSetChanged();
    }

    protected static class ViewHolder extends RecyclerView.ViewHolder {
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