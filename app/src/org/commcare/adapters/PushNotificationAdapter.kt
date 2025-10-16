package org.commcare.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.commcare.android.database.connect.models.PushNotificationRecord
import org.commcare.connect.ConnectConstants.CCC_DEST_DELIVERY_PROGRESS
import org.commcare.connect.ConnectConstants.CCC_DEST_LEARN_PROGRESS
import org.commcare.connect.ConnectConstants.CCC_DEST_OPPORTUNITY_SUMMARY_PAGE
import org.commcare.connect.ConnectConstants.CCC_DEST_PAYMENTS
import org.commcare.connect.ConnectConstants.CCC_MESSAGE
import org.commcare.connect.ConnectDateUtils.formatNotificationTime
import org.commcare.connect.database.NotificationRecordDatabaseHelper.updateReadStatus
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ItemPushNotificationBinding

class PushNotificationAdapter(
    private val listener: OnNotificationClickListener
) : ListAdapter<PushNotificationRecord, PushNotificationAdapter.PushNotificationViewHolder>(
    NotificationDiffCallback
) {
    inner class PushNotificationViewHolder(val binding: ItemPushNotificationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PushNotificationViewHolder {
        val binding = ItemPushNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PushNotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PushNotificationViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvNotification.text = item.body
            val iconRes = when (item.action) {
                CCC_DEST_PAYMENTS -> R.drawable.ic_dollar_payment_pn
                CCC_MESSAGE -> R.drawable.nav_drawer_message_icon
                CCC_DEST_OPPORTUNITY_SUMMARY_PAGE -> R.drawable.ic_connect_new_opportunity
                CCC_DEST_LEARN_PROGRESS -> R.drawable.ic_connect_learning
                CCC_DEST_DELIVERY_PROGRESS -> R.drawable.ic_delivery_pn
                else -> null
            }
            iconRes?.let { ivNotification.setImageResource(it) }
            ivNotification.imageTintList = if (item.action == CCC_MESSAGE) {
                ColorStateList.valueOf(ContextCompat.getColor(root.context, R.color.black))
            } else null

            tvTime.text = formatNotificationTime(this.clMain.context, item.createdDate)

            if (item.readStatus) {
                clMain.setBackgroundColor(
                    ContextCompat.getColor(clMain.context, R.color.connect_background_color)
                )
            } else {
                clMain.setBackgroundColor(
                    ContextCompat.getColor(clMain.context, R.color.white)
                )
            }

            clMain.setOnClickListener {
                item.readStatus = true
                notifyItemChanged(holder.adapterPosition)
                updateReadStatus(clMain.context, item.notificationId, true)
                listener.onNotificationClick(item)
            }
        }
    }

    interface OnNotificationClickListener {
        fun onNotificationClick(notificationRecord: PushNotificationRecord)
    }

    companion object NotificationDiffCallback : DiffUtil.ItemCallback<PushNotificationRecord>() {
        override fun areItemsTheSame(
            oldItem: PushNotificationRecord,
            newItem: PushNotificationRecord
        ): Boolean {
            return oldItem.notificationId == newItem.notificationId
        }

        override fun areContentsTheSame(
            oldItem: PushNotificationRecord,
            newItem: PushNotificationRecord
        ): Boolean {
            return oldItem.notificationId == newItem.notificationId
        }
    }
}