package org.commcare.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.commcare.android.database.connect.models.CredentialType
import org.commcare.android.database.connect.models.PersonalIdCredential
import org.commcare.dalvik.R
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
            tvCredentialTitle.text = item.appId
            tvAppName.text = item.appId
            tvIssuedDate.text = item.issuedDate
            tvUserActivity.text = item.level
            when (item.getCredentialType()) {
                CredentialType.LEARN -> {
                    view.setBackgroundColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.cc_dark_warm_accent_color
                        )
                    )
                }

                CredentialType.DELIVER -> {
                    view.setBackgroundColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.personal_id_delivery_app
                        )
                    )
                }

                CredentialType.APP_ACTIVITY -> {
                    view.setBackgroundColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.connect_message_large_icon_color
                        )
                    )
                }

                else -> {
                    view.setBackgroundColor(
                        ContextCompat.getColor(
                            root.context,
                            R.color.connect_message_large_icon_color
                        )
                    )
                }
            }

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
