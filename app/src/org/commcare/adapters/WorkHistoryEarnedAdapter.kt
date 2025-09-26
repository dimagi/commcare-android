package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.connect.ConnectDateUtils.convertIsoDate
import org.commcare.dalvik.databinding.ItemEarnedCredentialBinding
import org.commcare.utils.StringUtils

class WorkHistoryEarnedAdapter(
    private val listener: OnWorkHistoryItemClickListener,private val profilePic:String?
) : RecyclerView.Adapter<WorkHistoryEarnedAdapter.WorkHistoryViewHolder>() {

    private val workHistoryList = mutableListOf<PersonalIdWorkHistory>()

    inner class WorkHistoryViewHolder(val binding: ItemEarnedCredentialBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkHistoryViewHolder {
        val binding = ItemEarnedCredentialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WorkHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkHistoryViewHolder, position: Int) {
        val item = workHistoryList[position]
        val formattedIssuedDate: String =
            convertIsoDate(item.issuedDate, "dd/MM/yyyy")

        with(holder.binding) {
            tvAppName.text = item.title
            tvIssuedDate.text = formattedIssuedDate
            tvActivity.text = StringUtils.getLocalizedLevel(item.level, holder.itemView.context)
            Glide.with(ivProfilePic).load(profilePic).into(ivProfilePic)
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
