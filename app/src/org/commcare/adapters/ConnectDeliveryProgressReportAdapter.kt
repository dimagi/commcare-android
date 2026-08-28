package org.commcare.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ConnectDeliveryProgressItemBinding
import org.commcare.models.connect.ConnectDeliveryDetails

class ConnectDeliveryProgressReportAdapter(
    private val onDeliveryClicked: (ConnectDeliveryDetails) -> Unit,
) : RecyclerView.Adapter<ConnectDeliveryProgressReportAdapter.ProgressBarViewHolder>() {
    private val deliveryProgressList = mutableListOf<ConnectDeliveryDetails>()

    class ProgressBarViewHolder(
        val binding: ConnectDeliveryProgressItemBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ProgressBarViewHolder {
        val binding =
            ConnectDeliveryProgressItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ProgressBarViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ProgressBarViewHolder,
        position: Int,
    ) {
        val details = deliveryProgressList[position]
        val context = holder.itemView.context
        with(holder.binding) {
            linearProgressBar.setProgress(details.approvedPercentage.toFloat())
            linearProgressBar.setProgressColor(
                MaterialColors.getColor(holder.itemView, R.attr.connectStatusPositive),
            )
            tvDeliveryTitle.text = details.deliveryName
            tvApproved.text = details.approvedCount.toString()
            tvDeliveryTotalAmount.text = details.totalAmount
            tvRemaining.text = remainingText(context, details)
            rootView.setOnClickListener { onDeliveryClicked(details) }
        }
    }

    private fun remainingText(
        context: Context,
        details: ConnectDeliveryDetails,
    ): String {
        if (details.pendingCount <= 0) {
            return context.getString(R.string.connect_results_summary_visits_done)
        }
        return when (details.remainingDays) {
            0 -> {
                context.getString(R.string.connect_results_summary_days_over)
            }

            1 -> {
                context.getString(R.string.connect_results_summary_remaining_today, details.pendingCount)
            }

            2 -> {
                context.getString(R.string.connect_results_summary_remaining_tomorrow, details.pendingCount)
            }

            else -> {
                context.getString(
                    R.string.connect_results_summary_remaining_days,
                    details.pendingCount,
                    details.remainingDays,
                )
            }
        }
    }

    override fun getItemCount(): Int = deliveryProgressList.size

    fun updateData(newData: List<ConnectDeliveryDetails>) {
        deliveryProgressList.clear()
        deliveryProgressList.addAll(newData)
        notifyDataSetChanged()
    }
}
