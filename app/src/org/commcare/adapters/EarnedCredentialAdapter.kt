package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.connect.ConnectDateUtils.convertIsoDate
import org.commcare.dalvik.databinding.ItemEarnedCredentialBinding
import org.commcare.utils.StringUtils

class EarnedCredentialAdapter(
    private val listener: OnCredentialClickListener,private val profilePic:String?
) : RecyclerView.Adapter<EarnedCredentialAdapter.CredentialViewHolder>() {

    private val credentialList = mutableListOf<PersonalIdCredential>()

    inner class CredentialViewHolder(val binding: ItemEarnedCredentialBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CredentialViewHolder {
        val binding = ItemEarnedCredentialBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CredentialViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CredentialViewHolder, position: Int) {
        val item = credentialList[position]
        val formattedIssuedDate: String =
            convertIsoDate(item.issuedDate, "dd/MM/yyyy")

        with(holder.binding) {
            tvAppName.text = item.title
            tvIssuedDate.text = formattedIssuedDate
            tvActivity.text = StringUtils.getLocalizedLevel(item.level, holder.itemView.context)
            Glide.with(ivProfilePic).load(profilePic).into(ivProfilePic)
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
