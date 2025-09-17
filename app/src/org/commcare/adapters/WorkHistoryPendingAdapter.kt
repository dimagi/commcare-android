package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.commcare.android.database.connect.models.PersonalIdWorkHistory
import org.commcare.dalvik.databinding.ItemPendingCredentialBinding
import org.commcare.utils.StringUtils

class PendingCredentialAdapter(
    private val listener: OnCredentialClickListener
) : RecyclerView.Adapter<PendingCredentialAdapter.CredentialViewHolder>() {

    private val credentialList = mutableListOf<PersonalIdWorkHistory>()

    inner class CredentialViewHolder(val binding: ItemPendingCredentialBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CredentialViewHolder {
        val binding = ItemPendingCredentialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CredentialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CredentialViewHolder, position: Int) {
        val item = credentialList[position]
        with(holder.binding) {
            tvAppName.text = item.title
            tvActivity.text = StringUtils.getLocalizedLevel(item.level, holder.itemView.context)
        }
    }

    override fun getItemCount(): Int = credentialList.size

    fun setData(newList: List<PersonalIdWorkHistory>) {
        credentialList.clear()
        credentialList.addAll(newList)
        notifyDataSetChanged()
    }

    interface OnCredentialClickListener {
        fun onViewCredentialClick(credential: PersonalIdWorkHistory)
    }
}
