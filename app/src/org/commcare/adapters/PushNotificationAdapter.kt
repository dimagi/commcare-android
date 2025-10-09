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
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.ItemPushNotificationBinding

class PushNotificationAdapter(
    private val listener: OnNotificationClickListener
) : ListAdapter<PushNotificationRecord, PushNotificationAdapter.PushNotificationViewHolder>(
    DiffCallback
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
            tvNotification.text = item.title
            val iconRes = when (item.action) {
                CCC_DEST_PAYMENTS -> R.drawable.ic_dollar_payment_pn
                CCC_MESSAGE -> R.drawable.nav_drawer_message_icon
                CCC_DEST_OPPORTUNITY_SUMMARY_PAGE -> R.drawable.ic_connect_new_opportunity
                CCC_DEST_LEARN_PROGRESS -> R.drawable.ic_connect_learning
                CCC_DEST_DELIVERY_PROGRESS -> R.drawable.ic_delivery_pn
                else -> null
            }
            iconRes?.let { ivNotification.setImageResource(it) }
            if (item.action == CCC_MESSAGE) {
                ivNotification.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(holder.binding.root.context, R.color.black)
                )
            } else {
                ivNotification.imageTintList = null
            }

            root.setOnClickListener {
                listener.onNotificationClick(item)
            }
        }
    }

    interface OnNotificationClickListener {
        fun onNotificationClick(notificationRecord: PushNotificationRecord)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<PushNotificationRecord>() {
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