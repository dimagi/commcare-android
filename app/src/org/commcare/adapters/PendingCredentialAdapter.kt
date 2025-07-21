package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.dalvik.databinding.ItemPendingCredentialBinding

class PendingCredentialAdapter(
    private val listener: OnCredentialClickListener
) : RecyclerView.Adapter<PendingCredentialAdapter.CredentialViewHolder>() {

    private val credentialList = mutableListOf<PersonalIdCredential>()

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
            tvActivity.text = item.level
        }
    }

    override fun getItemCount(): Int = credentialList.size

    fun setData(newList: List<PersonalIdCredential>) {
        credentialList.clear()
        credentialList.addAll(newList)
        notifyDataSetChanged()
    }

    interface OnCredentialClickListener {
        fun onViewCredentialClick(credential: PersonalIdCredential)
    }
}
