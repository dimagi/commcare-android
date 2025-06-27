package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.dalvik.databinding.ItemPersonalIdCredentialBinding

class PersonalIdCredentialAdapter(
    private val listener: OnCredentialClickListener
) : RecyclerView.Adapter<PersonalIdCredentialAdapter.CredentialViewHolder>() {

    private val credentialList = mutableListOf<PersonalIdCredential>()

    inner class CredentialViewHolder(val binding: ItemPersonalIdCredentialBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CredentialViewHolder {
        val binding = ItemPersonalIdCredentialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CredentialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CredentialViewHolder, position: Int) {
        val item = credentialList[position]
        with(holder.binding) {
            tvCredentialTitle.text = item.title
            tvAppName.text = item.appName
            tvIssuedDate.text = item.issuedDate
            tvUserActivity.text = item.credential

            tvViewCredential.setOnClickListener {
                listener.onViewCredentialClick(item)
            }
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
