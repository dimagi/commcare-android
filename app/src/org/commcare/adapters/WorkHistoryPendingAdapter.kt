package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.dalvik.databinding.ItemPendingWorkHistoryBinding
import org.commcare.utils.StringUtils

class WorkHistoryPendingAdapter(
    private val listener: OnWorkHistoryItemClickListener
) : RecyclerView.Adapter<WorkHistoryPendingAdapter.WorkHistoryViewHolder>() {

    private val workHistoryList = mutableListOf<PersonalIdWorkHistory>()

    inner class WorkHistoryViewHolder(val binding: ItemPendingWorkHistoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkHistoryViewHolder {
        val binding = ItemPendingWorkHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WorkHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkHistoryViewHolder, position: Int) {
        val item = workHistoryList[position]
        with(holder.binding) {
            tvAppName.text = item.title
            tvActivity.text = StringUtils.getLocalizedLevel(item.level, holder.itemView.context)
        }
    }

    override fun getItemCount(): Int = workHistoryList.size

    fun setData(newList: List<PersonalIdWorkHistory>) {
        workHistoryList.clear()
        workHistoryList.addAll(newList)
        notifyDataSetChanged()
    }

    interface OnWorkHistoryItemClickListener {
        fun onWorkHistoryItemClick(workHistory: PersonalIdWorkHistory)
    }
}
