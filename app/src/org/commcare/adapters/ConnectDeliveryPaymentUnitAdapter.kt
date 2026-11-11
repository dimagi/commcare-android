package org.commcare.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ConnectDeliveryProgressItemBinding
import org.commcare.models.connect.ConnectDeliveryDetails

class ConnectDeliveryPaymentUnitAdapter(
    private val onPaymentUnitClicked: (ConnectDeliveryDetails) -> Unit,
) : RecyclerView.Adapter<ConnectDeliveryPaymentUnitAdapter.PaymentUnitViewHolder>() {
    private val paymentUnits = mutableListOf<ConnectDeliveryDetails>()

    class PaymentUnitViewHolder(
        val binding: ConnectDeliveryProgressItemBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): PaymentUnitViewHolder {
        val binding =
            ConnectDeliveryProgressItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return PaymentUnitViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: PaymentUnitViewHolder,
        position: Int,
    ) {
        val details = paymentUnits[position]
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
            rootView.setOnClickListener { onPaymentUnitClicked(details) }
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

    override fun getItemCount(): Int = paymentUnits.size

    fun updateData(newData: List<ConnectDeliveryDetails>) {
        paymentUnits.clear()
        paymentUnits.addAll(newData)
        notifyDataSetChanged()
    }
}
